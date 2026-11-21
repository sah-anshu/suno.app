// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.tv

import android.content.Context
import android.provider.Settings
import au.remotiq.suno.audiograph.RemoteAudioHub
import au.remotiq.suno.data.PreferenceStorage
import au.remotiq.suno.family.ShareSink
import au.remotiq.suno.link.ClientState
import au.remotiq.suno.link.CodecIds
import au.remotiq.suno.link.DeviceKinds
import au.remotiq.suno.link.FrameCodec
import au.remotiq.suno.link.JoinCode
import au.remotiq.suno.link.LinkClient
import au.remotiq.suno.link.PcmCodec
import au.remotiq.suno.link.StreamKinds
import au.remotiq.suno.link.TvProvisioningHost
import au.remotiq.suno.link.android.LinkConnector
import au.remotiq.suno.link.android.LinkHosting
import au.remotiq.suno.link.android.LinkPermissions
import au.remotiq.suno.link.android.LinkTransport
import au.remotiq.suno.link.android.OpusCodec
import au.remotiq.suno.link.android.ShareSource
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

/** What the TV is doing. */
data class TvStatus(
    /** Showing its setup code, waiting for a companion to hand over the patient's code. */
    val setupUri: String? = null,
    val patientName: String? = null,
    val state: ClientState = ClientState.IDLE,
    val transport: LinkTransport? = null,
    val sharing: Boolean = false,
    val tvName: String = "TV",
    /** True when this TV is older than Android 10 or lacks the radios Suno needs. */
    val unsupported: Boolean = false,
    /** True while the patient's SOS is on: the TV shows a banner (it cannot answer). */
    val sosActive: Boolean = false,
)

/**
 * Suno TV: until set up, shows a setup code and waits for a companion to hand over the patient's
 * join code (TVs have no camera). Then joins the patient's Suno as a TV and shares what the TV plays.
 */
@Singleton
class TvLink
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val preferences: PreferenceStorage,
    ) : ShareSink {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var hosting: LinkHosting? = null
        private var connector: LinkConnector? = null
        private var client: LinkClient? = null
        private var job: Job? = null

        private val _status = MutableStateFlow(TvStatus(tvName = tvName()))
        val status: StateFlow<TvStatus> = _status.asStateFlow()

        override val connected: StateFlow<Boolean> =
            _status.map { it.state == ClientState.CONNECTED }.stateIn(scope, SharingStarted.Eagerly, false)

        /** Call once permissions are granted. Does nothing while already set up, connecting or connected. */
        fun start() {
            if (hosting != null || job?.isActive == true || client != null) return
            restart()
        }

        /** Connects again (or shows the setup code) even if a connection is open. */
        fun retry() = restart()

        private fun restart() {
            if (!LinkPermissions.supported()) {
                _status.update { it.copy(unsupported = true) }
                return
            }
            scope.launch {
                val patient = preferences.observeLinkIdentity().first()?.let(JoinCode::parse)
                if (patient == null) beginSetup() else connect(patient)
            }
        }

        /** Forget the patient's Suno and show a new setup code. */
        fun reset() {
            stopAll()
            scope.launch {
                preferences.setLinkIdentity("")
                beginSetup()
            }
        }

        override fun sendMedia(frame: ShortArray) {
            client?.sendFrame(frame, StreamKinds.MEDIA)
        }

        override fun onSharing(source: ShareSource?) {
            _status.update { it.copy(sharing = source != null) }
        }

        private suspend fun beginSetup() {
            stopAll()
            val setup = JoinCode.generate(tvName(), tvSetup = true)
            _status.value = TvStatus(setupUri = setup.toUri(), tvName = setup.patientName)
            val host =
                TvProvisioningHost(setup) { patient ->
                    scope.launch {
                        hosting?.stop()
                        hosting = null
                        preferences.setLinkIdentity(patient.toUri())
                        connect(patient)
                    }
                }
            hosting = LinkHosting(context, setup).also { it.start(host) }
        }

        private fun connect(patient: JoinCode) {
            stopAll()
            _status.value =
                TvStatus(patientName = patient.patientName, state = ClientState.CONNECTING, tvName = tvName())
            job =
                scope.launch {
                    val opened = LinkConnector(context).also { connector = it }.open(patient)
                    if (opened == null) {
                        _status.update { it.copy(state = ClientState.CLOSED) }
                        return@launch
                    }
                    val (channel, transport) = opened
                    val linkClient = LinkClient(patient, clientId(), tvName(), codecs(), deviceKind = DeviceKinds.TV)
                    client = linkClient
                    launch {
                        linkClient.state.collect { s ->
                            _status.update { it.copy(state = s, transport = transport) }
                        }
                    }
                    launch {
                        linkClient.sos.collect { alert ->
                            if (alert != null) _status.update { it.copy(sosActive = alert.active) }
                        }
                    }
                    linkClient.connect(channel)
                }
        }

        private fun stopAll() {
            job?.cancel()
            client?.leave()
            client = null
            connector?.leave()
            connector = null
            hosting?.stop()
            hosting = null
        }

        private fun codecs(): Map<Int, () -> FrameCodec> =
            buildMap {
                put(CodecIds.PCM16) { PcmCodec(RemoteAudioHub.FRAME_SAMPLES) }
                if (LinkPermissions.supported()) put(CodecIds.OPUS) { OpusCodec(RemoteAudioHub.FRAME_SAMPLES) }
            }

        private suspend fun clientId(): String =
            preferences.observeLinkClientId().first()
                ?: UUID.randomUUID().toString().also { preferences.setLinkClientId(it) }

        /** The name set in the TV's own settings (for example "Living room TV"). */
        private fun tvName(): String =
            Settings.Global
                .getString(context.contentResolver, DEVICE_NAME)
                ?.takeIf { it.isNotBlank() }
                ?.take(MAX_NAME) ?: "TV"

        private companion object {
            const val DEVICE_NAME = "device_name"
            const val MAX_NAME = 40
        }
    }
