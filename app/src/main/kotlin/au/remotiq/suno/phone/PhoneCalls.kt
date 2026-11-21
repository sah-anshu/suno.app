// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.phone

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.core.content.ContextCompat
import au.remotiq.suno.data.CallSettings
import au.remotiq.suno.data.PreferenceStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

enum class CallPhase { RINGING, DIALING, ACTIVE, HOLDING, ENDED }

/** Where call sound goes. Hearing aids connect as Bluetooth. */
enum class CallRoute { PHONE, SPEAKER, EARPHONES, BLUETOOTH }

/** What the call screens show. */
data class CallUi(
    val caller: Caller,
    val phase: CallPhase,
    val incoming: Boolean,
    /** When the call was answered (elapsed real time base of System.currentTimeMillis). */
    val connectedAt: Long? = null,
    val muted: Boolean = false,
    val route: CallRoute = CallRoute.PHONE,
    val routes: Set<CallRoute> = setOf(CallRoute.PHONE, CallRoute.SPEAKER),
    val volume: Int = 0,
    val maxVolume: Int = 0,
)

/**
 * The patient's phone calls while Suno is the phone app. [SunoInCallService] reports calls here;
 * the call screens read [call] and send Answer, Decline, End, mute, route and volume back. Suno never
 * gets the call audio: Android routes it (arch.md section 6.1).
 */
@Singleton
class PhoneCalls
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val preferences: PreferenceStorage,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val audio = context.getSystemService(AudioManager::class.java)
        private var service: InCallService? = null
        private var current: Call? = null
        private var autoAnswer: Job? = null

        private val _call = MutableStateFlow<CallUi?>(null)
        val call: StateFlow<CallUi?> = _call.asStateFlow()

        /** True while an SOS is on: every call rings. Set by the SOS controller. */
        @Volatile var sosActive: Boolean = false

        private val callback =
            object : Call.Callback() {
                override fun onStateChanged(
                    call: Call,
                    state: Int,
                ) = publish(call)
            }

        fun isDefaultPhoneApp(): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_DIALER) == true
            } else {
                context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage == context.packageName
            }

        /** Calls straight away when allowed; otherwise opens the phone app with the number filled in. */
        @SuppressLint("MissingPermission")
        fun placeCall(number: String) {
            val uri = Uri.fromParts("tel", number, null)
            val telecom = context.getSystemService(TelecomManager::class.java)
            val allowed =
                ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
                    PackageManager.PERMISSION_GRANTED
            if (allowed && telecom != null && runCatching { telecom.placeCall(uri, null) }.isSuccess) return
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }

        /**
         * Calls [number] and waits: true if answered, false if not answered within [limitMs] or ended
         * first. Null when Suno is not the phone app and cannot see the call.
         */
        suspend fun callAndWait(
            number: String,
            limitMs: Long = CallRules.SOS_ANSWER_LIMIT_MS,
        ): Boolean? {
            val watching = isDefaultPhoneApp()
            placeCall(number)
            if (!watching) return null
            val outgoing = withTimeoutOrNull(START_WAIT_MS) { waitFor { it != null && !it.incoming } }
            val ours = outgoing != null && PhoneNumbers.same(outgoing.caller.number, number)
            val result =
                if (ours) {
                    withTimeoutOrNull(limitMs) {
                        waitFor { it == null || it.phase == CallPhase.ACTIVE || it.phase == CallPhase.ENDED }
                    }
                } else {
                    null
                }
            if (ours && result == null) end()
            return result?.phase == CallPhase.ACTIVE
        }

        private suspend fun waitFor(predicate: (CallUi?) -> Boolean): CallUi? {
            var found: CallUi? = null
            _call.first { ui -> predicate(ui).also { if (it) found = ui } }
            return found
        }

        fun answer() {
            current?.answer(VideoProfile.STATE_AUDIO_ONLY)
        }

        fun decline() {
            current?.reject(false, null)
        }

        fun end() {
            current?.disconnect()
        }

        fun setMuted(muted: Boolean) {
            service?.setMuted(muted)
            _call.update { it?.copy(muted = muted) }
        }

        @Suppress("DEPRECATION")
        fun setRoute(route: CallRoute) {
            service?.setAudioRoute(
                when (route) {
                    CallRoute.PHONE -> CallAudioState.ROUTE_EARPIECE
                    CallRoute.SPEAKER -> CallAudioState.ROUTE_SPEAKER
                    CallRoute.EARPHONES -> CallAudioState.ROUTE_WIRED_HEADSET
                    CallRoute.BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
                },
            )
        }

        fun changeVolume(up: Boolean) {
            audio.adjustStreamVolume(
                AudioManager.STREAM_VOICE_CALL,
                if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                0,
            )
            _call.update { it?.copy(volume = audio.getStreamVolume(AudioManager.STREAM_VOICE_CALL)) }
        }

        // Called by SunoInCallService.

        internal fun onServiceBound(bound: InCallService?) {
            service = bound
        }

        internal suspend fun onCallAdded(call: Call): Boolean {
            if (current != null && current != call) {
                // One call at a time keeps the screens simple: a second incoming call is declined.
                if (call.details.callDirection == Call.Details.DIRECTION_INCOMING) call.reject(false, null)
                return false
            }
            val number =
                call.details.handle
                    ?.schemeSpecificPart
                    .orEmpty()
            val settings = preferences.observeCallSettings().first()
            val caller = CallRules.identify(number, settings.favourites, contactName(number))
            val incoming = call.details.callDirection == Call.Details.DIRECTION_INCOMING
            if (incoming && CallRules.shouldBlock(caller, settings, sosActive)) {
                call.reject(false, null)
                return false
            }
            current = call
            call.registerCallback(callback)
            _call.value =
                CallUi(
                    caller = caller,
                    phase = phaseOf(call),
                    incoming = incoming,
                    volume = audio.getStreamVolume(AudioManager.STREAM_VOICE_CALL),
                    maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL),
                )
            if (incoming && CallRules.shouldAutoAnswer(caller, settings)) scheduleAutoAnswer(call)
            return true
        }

        internal fun onCallRemoved(call: Call) {
            call.unregisterCallback(callback)
            if (call != current) return
            autoAnswer?.cancel()
            current = null
            _call.update { it?.copy(phase = CallPhase.ENDED) }
            scope.launch {
                delay(ENDED_SHOWN_MS)
                if (current == null) _call.value = null
            }
        }

        internal fun onAudioState(state: CallAudioState?) {
            state ?: return
            val routes =
                buildSet {
                    if (state.supportedRouteMask and CallAudioState.ROUTE_EARPIECE != 0) add(CallRoute.PHONE)
                    if (state.supportedRouteMask and CallAudioState.ROUTE_SPEAKER != 0) add(CallRoute.SPEAKER)
                    if (state.supportedRouteMask and CallAudioState.ROUTE_WIRED_HEADSET != 0) add(CallRoute.EARPHONES)
                    if (state.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH != 0) add(CallRoute.BLUETOOTH)
                }
            val route =
                when (state.route) {
                    CallAudioState.ROUTE_SPEAKER -> CallRoute.SPEAKER
                    CallAudioState.ROUTE_WIRED_HEADSET -> CallRoute.EARPHONES
                    CallAudioState.ROUTE_BLUETOOTH -> CallRoute.BLUETOOTH
                    else -> CallRoute.PHONE
                }
            _call.update { it?.copy(route = route, routes = routes, muted = state.isMuted) }
        }

        private fun publish(call: Call) {
            if (call != current) return
            val phase = phaseOf(call)
            _call.update {
                it?.copy(
                    phase = phase,
                    connectedAt = it.connectedAt ?: System.currentTimeMillis().takeIf { phase == CallPhase.ACTIVE },
                )
            }
            if (phase != CallPhase.RINGING) autoAnswer?.cancel()
        }

        /** Answers after about three rings, on earphones if connected, otherwise on the speaker. */
        private fun scheduleAutoAnswer(call: Call) {
            autoAnswer =
                scope.launch {
                    delay(CallRules.AUTO_ANSWER_MS)
                    if (current == call && phaseOf(call) == CallPhase.RINGING) {
                        call.answer(VideoProfile.STATE_AUDIO_ONLY)
                        delay(ROUTE_DELAY_MS)
                        val routes = _call.value?.routes.orEmpty()
                        setRoute(
                            when {
                                CallRoute.BLUETOOTH in routes -> CallRoute.BLUETOOTH
                                CallRoute.EARPHONES in routes -> CallRoute.EARPHONES
                                else -> CallRoute.SPEAKER
                            },
                        )
                    }
                }
        }

        @Suppress("DEPRECATION")
        private fun phaseOf(call: Call): CallPhase =
            when (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) call.details.state else call.state) {
                Call.STATE_RINGING -> CallPhase.RINGING
                Call.STATE_ACTIVE -> CallPhase.ACTIVE
                Call.STATE_HOLDING -> CallPhase.HOLDING
                Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> CallPhase.ENDED
                else -> CallPhase.DIALING
            }

        /** The phone contact's name, if contacts may be read. */
        private suspend fun contactName(number: String): String? {
            if (number.isBlank() ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return null
            }
            return withContext(Dispatchers.IO) {
                runCatching {
                    val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
                    context.contentResolver
                        .query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                        ?.use { if (it.moveToFirst()) it.getString(0) else null }
                }.getOrNull()
            }
        }

        /** For the call screening service: whether this incoming number should ring. */
        internal suspend fun shouldBlock(number: String): Boolean {
            val settings: CallSettings = preferences.observeCallSettings().first()
            if (!settings.contactsOnly) return false
            return CallRules.shouldBlock(
                CallRules.identify(number, settings.favourites, contactName(number)),
                settings,
                sosActive,
            )
        }

        private companion object {
            const val START_WAIT_MS = 10_000L
            const val ENDED_SHOWN_MS = 1_500L
            const val ROUTE_DELAY_MS = 500L
        }
    }
