// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.family

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import au.remotiq.suno.audiograph.RemoteAudioHub
import au.remotiq.suno.data.PreferenceStorage
import au.remotiq.suno.link.ClientState
import au.remotiq.suno.link.CodecIds
import au.remotiq.suno.link.FrameCodec
import au.remotiq.suno.link.JoinCode
import au.remotiq.suno.link.LinkChannel
import au.remotiq.suno.link.LinkClient
import au.remotiq.suno.link.PcmCodec
import au.remotiq.suno.link.SosAlert
import au.remotiq.suno.link.StreamKinds
import au.remotiq.suno.link.TvProvisioningClient
import au.remotiq.suno.link.android.LinkConnector
import au.remotiq.suno.link.android.LinkPermissions
import au.remotiq.suno.link.android.LinkTransport
import au.remotiq.suno.link.android.OpusCodec
import au.remotiq.suno.link.android.RelayHosting
import au.remotiq.suno.link.android.ShareSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** How the companion app is connected to the patient's Suno. */
data class CompanionStatus(
    val patientName: String? = null,
    val state: ClientState = ClientState.IDLE,
    val transport: LinkTransport? = null,
    val talking: Boolean = false,
    /** What this phone is sharing with the patient, if anything. */
    val sharing: ShareSource? = null,
    /** Progress of handing the patient's join code to a TV. */
    val tvSetup: TvProvisioningClient.State = TvProvisioningClient.State.IDLE,
    /** The patient's latest SOS; kept when it ends (inactive) until dismissed. */
    val sos: SosAlert? = null,
    /** This phone's name, to tell whether it already answered the SOS. */
    val myName: String = "",
)

/**
 * Companion side of the family link: joins the patient's Suno from a scanned code, preferring
 * Wi-Fi Direct and falling back to Bluetooth LE, then carries this phone's voice. Sends clean
 * audio; the patient's phone applies the patient's audiogram.
 */
@Singleton
class CompanionLink
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val preferences: PreferenceStorage,
    ) : ShareSink {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var client: LinkClient? = null
        private var connector: LinkConnector? = null
        private var connecting: Job? = null
        private var retry: Job? = null
        private var relay: RelayHosting? = null
        private var currentCode: JoinCode? = null

        /** True while this phone should stay linked (joined and not left). */
        @Volatile private var wanted = false

        /** True while handing the code to a TV, when the link is down on purpose. */
        @Volatile private var settingUpTv = false
        private var attempt = 0

        private val _status = MutableStateFlow(CompanionStatus())
        val status: StateFlow<CompanionStatus> = _status.asStateFlow()

        init {
            scope.launch {
                savedCode()?.let { code ->
                    _status.update { it.copy(patientName = code.patientName) }
                    wanted = true
                    FamilyLinkService.start(context)
                    connect(code)
                }
            }
            // Reconnect on its own while linked, waiting longer after each failed attempt.
            scope.launch {
                _status.map { it.state }.distinctUntilChanged().collect { state ->
                    when (state) {
                        ClientState.CONNECTED -> {
                            attempt = 0
                            startRelay()
                        }
                        ClientState.CLOSED -> {
                            stopRelay()
                            if (wanted && !settingUpTv) scheduleRetry()
                        }
                        ClientState.DENIED -> wanted = false
                        else -> Unit
                    }
                }
            }
        }

        /** "I'm on my way" for the patient's current SOS. */
        fun replyOnMyWay() {
            client?.replyOnMyWay()
        }

        /** Hides an SOS that has ended. */
        fun dismissSos() {
            _status.update { if (it.sos?.active == true) it else it.copy(sos = null) }
        }

        private fun scheduleRetry() {
            retry?.cancel()
            val wait = RETRY_MS[minOf(attempt, RETRY_MS.lastIndex)]
            attempt++
            retry =
                scope.launch {
                    delay(wait)
                    savedCode()?.let { if (wanted) connect(it) }
                }
        }

        private fun startRelay() {
            val code = currentCode ?: return
            val via = connector ?: return
            if (relay != null || _status.value.transport == LinkTransport.RELAY || !LinkPermissions.supported()) return
            relay = RelayHosting(context, code, via, scope).takeIf { it.start() }
        }

        // Link objects are only created on Android 10+ (LinkPermissions.supported()), so these calls never run earlier.
        @SuppressLint("NewApi")
        private fun stopRelay() {
            relay?.stop()
            relay = null
        }

        suspend fun savedCode(): JoinCode? = preferences.observeLinkIdentity().first()?.let(JoinCode::parse)

        /** Starts joining from a scanned QR code. Returns false if it is not a Suno join code. */
        fun join(scanned: String): Boolean {
            val code = JoinCode.parse(scanned) ?: return false
            scope.launch {
                preferences.setLinkIdentity(code.toUri())
                wanted = true
                attempt = 0
                FamilyLinkService.start(context)
                connect(code)
            }
            return true
        }

        fun reconnect() {
            scope.launch {
                savedCode()?.let {
                    wanted = true
                    attempt = 0
                    FamilyLinkService.start(context)
                    connect(it)
                }
            }
        }

        /** Disconnect and forget the patient's Suno on this phone. */
        fun leave() {
            wanted = false
            retry?.cancel()
            disconnect()
            FamilyLinkService.stop(context)
            scope.launch { preferences.setLinkIdentity("") }
            _status.value = CompanionStatus()
        }

        fun sendFrame(pcm: ShortArray) {
            client?.sendFrame(pcm)
        }

        fun setTalking(on: Boolean) {
            _status.update { it.copy(talking = on) }
        }

        override val connected: StateFlow<Boolean> =
            _status.map { it.state == ClientState.CONNECTED }.stateIn(scope, SharingStarted.Eagerly, false)

        override fun sendMedia(frame: ShortArray) {
            client?.sendFrame(frame, StreamKinds.MEDIA)
        }

        override fun onSharing(source: ShareSource?) {
            _status.update { it.copy(sharing = source) }
        }

        // Link objects are only created on Android 10+ (LinkPermissions.supported()), so these calls never run earlier.
        @SuppressLint("NewApi")
        private fun disconnect() {
            stopRelay()
            connecting?.cancel()
            client?.leave()
            client = null
            connector?.leave()
            connector = null
        }

        private fun connect(code: JoinCode) {
            retry?.cancel()
            disconnect()
            currentCode = code
            _status.update {
                CompanionStatus(
                    patientName = code.patientName,
                    state = ClientState.CONNECTING,
                    tvSetup = it.tvSetup,
                    sos = it.sos,
                    myName = it.myName,
                )
            }
            connecting =
                scope.launch {
                    if (!LinkPermissions.supported()) {
                        _status.update { it.copy(state = ClientState.CLOSED) }
                        return@launch
                    }
                    val (channel, transport) =
                        openChannel(code) ?: run {
                            _status.update { it.copy(state = ClientState.CLOSED) }
                            return@launch
                        }
                    val name = deviceName()
                    val linkClient = LinkClient(code, clientId(), name, codecs())
                    client = linkClient
                    _status.update { it.copy(myName = name) }
                    launch {
                        linkClient.state.collect { s ->
                            _status.update { it.copy(state = s, transport = transport) }
                        }
                    }
                    launch {
                        // A new connection starts with no SOS; keep the last one until a new message.
                        linkClient.sos.filterNotNull().collect { alert -> _status.update { it.copy(sos = alert) } }
                    }
                    linkClient.connect(channel)
                }
        }

        private suspend fun openChannel(code: JoinCode): Pair<LinkChannel, LinkTransport>? {
            if (!LinkPermissions.supported()) return null
            return LinkConnector(context).also { connector = it }.open(code)
        }

        /**
         * Hands the patient's join code to a TV whose setup code was scanned. The phone briefly leaves
         * the patient's link (only one Wi-Fi Direct group at a time), then reconnects.
         */
        fun setUpTv(scanned: String): Boolean {
            val tvCode = JoinCode.parse(scanned)?.takeIf { it.tvSetup } ?: return false
            if (!LinkPermissions.supported()) return false
            scope.launch {
                val patient = savedCode() ?: return@launch
                settingUpTv = true
                disconnect()
                _status.update { it.copy(tvSetup = TvProvisioningClient.State.SENDING) }
                @SuppressLint("NewApi") // Checked above: setUpTv returns early before Android 10.
                val tvConnector = LinkConnector(context)
                val result =
                    tvConnector.open(tvCode)?.let { (channel, _) ->
                        val provisioning = TvProvisioningClient(tvCode, patient)
                        provisioning.send(channel)
                        withTimeoutOrNull(SETUP_TIMEOUT_MS) {
                            provisioning.state.first {
                                it !=
                                    TvProvisioningClient.State.SENDING
                            }
                        }
                    } ?: TvProvisioningClient.State.FAILED
                tvConnector.leave()
                _status.update { it.copy(tvSetup = result) }
                settingUpTv = false
                connect(patient)
            }
            return true
        }

        private fun codecs(): Map<Int, () -> FrameCodec> =
            buildMap {
                put(CodecIds.PCM16) { PcmCodec(RemoteAudioHub.FRAME_SAMPLES) }
                if (LinkPermissions.supported()) put(CodecIds.OPUS) { OpusCodec(RemoteAudioHub.FRAME_SAMPLES) }
            }

        private suspend fun clientId(): String =
            preferences.observeLinkClientId().first()
                ?: UUID.randomUUID().toString().also { preferences.setLinkClientId(it) }

        private suspend fun deviceName(): String =
            preferences
                .observePatientSettings()
                .first()
                .displayName
                .ifBlank { Build.MODEL ?: "Suno Companion" }

        private companion object {
            const val SETUP_TIMEOUT_MS = 10_000L

            /** Waits between reconnect attempts: the patient's phone hosts while listening or in SOS. */
            val RETRY_MS = longArrayOf(15_000L, 30_000L, 60_000L)
        }
    }
