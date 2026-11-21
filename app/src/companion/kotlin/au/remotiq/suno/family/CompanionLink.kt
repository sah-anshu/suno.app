// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.family

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
import au.remotiq.suno.link.StreamKinds
import au.remotiq.suno.link.android.BluetoothJoiner
import au.remotiq.suno.link.android.LinkPermissions
import au.remotiq.suno.link.android.OpusCodec
import au.remotiq.suno.link.android.ShareSource
import au.remotiq.suno.link.android.WifiDirectJoiner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** How the companion app is connected to the patient's Suno. */
data class CompanionStatus(
    val patientName: String? = null,
    val state: ClientState = ClientState.IDLE,
    val transport: Transport? = null,
    val talking: Boolean = false,
    /** What this phone is sharing with the patient, if anything. */
    val sharing: ShareSource? = null,
) {
    enum class Transport { WIFI_DIRECT, BLUETOOTH }
}

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
        private var wifi: WifiDirectJoiner? = null
        private var connecting: Job? = null

        private val _status = MutableStateFlow(CompanionStatus())
        val status: StateFlow<CompanionStatus> = _status.asStateFlow()

        init {
            scope.launch { savedCode()?.let { code -> _status.update { it.copy(patientName = code.patientName) } } }
        }

        suspend fun savedCode(): JoinCode? = preferences.observeLinkIdentity().first()?.let(JoinCode::parse)

        /** Starts joining from a scanned QR code. Returns false if it is not a Suno join code. */
        fun join(scanned: String): Boolean {
            val code = JoinCode.parse(scanned) ?: return false
            scope.launch {
                preferences.setLinkIdentity(code.toUri())
                connect(code)
            }
            return true
        }

        fun reconnect() {
            scope.launch { savedCode()?.let { connect(it) } }
        }

        /** Disconnect and forget the patient's Suno on this phone. */
        fun leave() {
            disconnect()
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

        private fun disconnect() {
            connecting?.cancel()
            client?.leave()
            client = null
            wifi?.leave()
            wifi = null
        }

        private fun connect(code: JoinCode) {
            disconnect()
            _status.value = CompanionStatus(patientName = code.patientName, state = ClientState.CONNECTING)
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
                    val linkClient = LinkClient(code, clientId(), deviceName(), codecs())
                    client = linkClient
                    launch {
                        linkClient.state.collect { s ->
                            _status.update { it.copy(state = s, transport = transport) }
                        }
                    }
                    linkClient.connect(channel)
                }
        }

        private suspend fun openChannel(code: JoinCode): Pair<LinkChannel, CompanionStatus.Transport>? {
            if (!LinkPermissions.supported()) return null
            code.wifiDirect?.let { credentials ->
                val joiner = WifiDirectJoiner(context)
                runCatching { joiner.join(credentials) }.getOrNull()?.let {
                    wifi = joiner
                    return it to CompanionStatus.Transport.WIFI_DIRECT
                }
                joiner.leave()
            }
            if (code.bluetooth) {
                runCatching { BluetoothJoiner(context).join(code.sessionId) }.getOrNull()?.let {
                    return it to CompanionStatus.Transport.BLUETOOTH
                }
            }
            return null
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
    }
