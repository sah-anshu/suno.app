// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.family

import android.content.Context
import au.remotiq.suno.audiograph.RemoteAudioHub
import au.remotiq.suno.data.PreferenceStorage
import au.remotiq.suno.data.StoredCompanion
import au.remotiq.suno.link.ApprovedCompanions
import au.remotiq.suno.link.CodecIds
import au.remotiq.suno.link.CompanionDevice
import au.remotiq.suno.link.FrameCodec
import au.remotiq.suno.link.GeoPoint
import au.remotiq.suno.link.JoinCode
import au.remotiq.suno.link.LinkHost
import au.remotiq.suno.link.PcmCodec
import au.remotiq.suno.link.android.LinkHosting
import au.remotiq.suno.link.android.LinkPermissions
import au.remotiq.suno.link.android.LinkTransport
import au.remotiq.suno.link.android.OpusCodec
import au.remotiq.suno.sos.SosLink
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the patient's phone can be reached right now, and over which radios. */
data class HostStatus(
    val running: Boolean = false,
    val wifiDirect: Boolean = false,
    val bluetooth: Boolean = false,
    /** True when the phone is older than Android 10 or permissions are missing. */
    val unavailable: Boolean = false,
)

/**
 * The patient phone's side of the family link. Runs the Wi-Fi Direct and Bluetooth LE hosts while
 * anyone holds it (the listening session, or the join-code screen), and routes approved
 * companions' voices into [hub]. Only receives: the patient's microphone is never sent.
 */
@Singleton
class PatientLinkHost
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val preferences: PreferenceStorage,
        private val hub: RemoteAudioHub,
    ) : SosLink {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val mutex = Mutex()
        private val holders = mutableSetOf<String>()
        private val approvedCache = ConcurrentHashMap<String, String>()
        private var host: LinkHost? = null
        private var hosting: LinkHosting? = null

        /** The SOS to send once the host is up (it starts asynchronously). */
        @Volatile private var pendingSos: Pair<Long, GeoPoint?>? = null

        private val _sosResponders = MutableStateFlow<List<String>>(emptyList())
        override val sosResponders: StateFlow<List<String>> = _sosResponders.asStateFlow()

        private val _status = MutableStateFlow(HostStatus())
        val status: StateFlow<HostStatus> = _status.asStateFlow()

        private val _pending = MutableStateFlow<List<CompanionDevice>>(emptyList())
        val pending: StateFlow<List<CompanionDevice>> = _pending.asStateFlow()

        private val _connected = MutableStateFlow<List<CompanionDevice>>(emptyList())
        override val connected: StateFlow<List<CompanionDevice>> = _connected.asStateFlow()

        /** Remote sources sounding right now: voices and shared sound such as TV. */
        val remote get() = hub.active

        /** The join code with the patient's current display name. */
        suspend fun joinCode(): JoinCode {
            val code = identity()
            val name =
                preferences
                    .observePatientSettings()
                    .first()
                    .displayName
                    .ifBlank { DEFAULT_NAME }
            return JoinCode(code.sessionId, code.secret, name, code.wifiDirect, code.bluetooth)
        }

        fun acquire(reason: String) {
            val first = synchronized(holders) { holders.add(reason) && holders.size == 1 }
            if (first) scope.launch { start() }
        }

        fun release(reason: String) {
            val last = synchronized(holders) { holders.remove(reason) && holders.isEmpty() }
            if (last) scope.launch { stop() }
        }

        /** Starts hosting (if needed) and alerts every connected device; phones that reconnect are told too. */
        override fun sendSos(
            id: Long,
            location: GeoPoint?,
        ) {
            pendingSos = id to location
            acquire(SOS)
            host?.sendSos(id, location)
        }

        override fun endSos() {
            pendingSos = null
            host?.endSos()
            _sosResponders.value = emptyList()
            // Give the "safe now" message time to leave before the radios stop (if nothing else holds them).
            scope.launch {
                delay(END_FLUSH_MS)
                release(SOS)
            }
        }

        fun allow(clientId: String) {
            host?.allow(clientId)
        }

        fun deny(clientId: String) {
            host?.deny(clientId)
        }

        /** The helper removed a companion: forget it and disconnect it. */
        fun remove(clientId: String) {
            approvedCache.remove(clientId)
            scope.launch { persistApproved() }
            host?.disconnect(clientId)
        }

        private suspend fun start() =
            mutex.withLock {
                if (!LinkPermissions.supported() ||
                    LinkPermissions.missing(context, LinkPermissions.host()).isNotEmpty()
                ) {
                    _status.value = HostStatus(unavailable = true)
                    return@withLock
                }
                val code = identity()
                preferences.observeApprovedCompanions().first().forEach { approvedCache[it.id] = it.name }
                val linkHost =
                    host ?: LinkHost(code, hub, Approved(), codecFactories()).also { created ->
                        host = created
                        scope.launch { created.pending.collect { _pending.value = it } }
                        scope.launch { created.connected.collect { _connected.value = it } }
                        scope.launch { created.sosState.collect { _sosResponders.value = it?.responders.orEmpty() } }
                    }
                pendingSos?.let { (id, location) -> linkHost.sendSos(id, location) }
                val radios = LinkHosting(context, code).also { hosting = it }.start(linkHost)
                _status.value =
                    HostStatus(
                        running = radios.isNotEmpty(),
                        wifiDirect = LinkTransport.WIFI_DIRECT in radios,
                        bluetooth = LinkTransport.BLUETOOTH in radios,
                    )
            }

        private suspend fun stop() =
            mutex.withLock {
                host?.closeAll()
                hosting?.stop()
                hosting = null
                _status.value = HostStatus()
            }

        private suspend fun identity(): JoinCode {
            preferences
                .observeLinkIdentity()
                .first()
                ?.let(JoinCode::parse)
                ?.let { return it }
            val created = JoinCode.generate(DEFAULT_NAME)
            preferences.setLinkIdentity(created.toUri())
            return created
        }

        private fun codecFactories(): Map<Int, () -> FrameCodec> =
            buildMap {
                put(CodecIds.PCM16) { PcmCodec(RemoteAudioHub.FRAME_SAMPLES) }
                if (LinkPermissions.supported()) put(CodecIds.OPUS) { OpusCodec(RemoteAudioHub.FRAME_SAMPLES) }
            }

        private suspend fun persistApproved() {
            preferences.setApprovedCompanions(approvedCache.map { (id, name) -> StoredCompanion(id, name) })
        }

        private inner class Approved : ApprovedCompanions {
            override fun isApproved(clientId: String) = approvedCache.containsKey(clientId)

            override fun approve(device: CompanionDevice) {
                approvedCache[device.clientId] = device.deviceName
                scope.launch { persistApproved() }
            }
        }

        private companion object {
            const val DEFAULT_NAME = "Suno"
            const val SOS = "sos"
            const val END_FLUSH_MS = 2_000L
        }
    }
