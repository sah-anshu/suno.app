// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.link

import au.remotiq.suno.audiograph.RemoteAudioHub
import au.remotiq.suno.audiograph.StreamKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/** A phone asking to join, or one that is connected. */
data class CompanionDevice(
    val clientId: String,
    val deviceName: String,
    val deviceKind: Int = DeviceKinds.PHONE,
)

/** Companions the patient's phone has allowed before. They rejoin without asking again. */
interface ApprovedCompanions {
    fun isApproved(clientId: String): Boolean

    fun approve(device: CompanionDevice)
}

/**
 * The patient's side of the link. Accepts connections from any transport, asks the patient before
 * a new phone may join, and feeds approved companions' voices into [hub]. Audio from anyone not
 * approved, or that fails decryption, is dropped silently. Nothing is ever sent from the patient's
 * microphone.
 */
class LinkHost(
    private val code: JoinCode,
    private val hub: RemoteAudioHub,
    private val approved: ApprovedCompanions,
    /** One new codec per connection: decoders such as Opus keep state per stream. */
    private val codecs: Map<Int, () -> FrameCodec>,
    private val random: SecureRandom = SecureRandom(),
) : LinkServer.Listener {
    private val _pending = MutableStateFlow<List<CompanionDevice>>(emptyList())
    private val _connected = MutableStateFlow<List<CompanionDevice>>(emptyList())

    /** Phones waiting for the patient to allow or refuse them. */
    val pending: StateFlow<List<CompanionDevice>> = _pending.asStateFlow()

    /** Phones that are allowed and connected. */
    val connected: StateFlow<List<CompanionDevice>> = _connected.asStateFlow()

    @Volatile private var sos: SosAlert? = null

    private val _sosState = MutableStateFlow<SosAlert?>(null)

    /** The SOS sent to everyone, with the names of those on their way. Null when there is none. */
    val sosState: StateFlow<SosAlert?> = _sosState.asStateFlow()

    private val connections = ConcurrentHashMap.newKeySet<Connection>()
    private val byTag = ConcurrentHashMap<Int, Connection>()

    override fun onConnection(channel: LinkChannel) {
        val connection = Connection(channel)
        connections += connection
        channel.start(connection)
    }

    override fun onAudio(frame: ByteArray) {
        val connection =
            frame.takeIf { it.size > Int.SIZE_BYTES }?.let {
                byTag[
                    ByteBuffer
                        .wrap(
                            it,
                            0,
                            Int.SIZE_BYTES,
                        ).int,
                ]
            }
        val device = connection?.device
        val audio =
            connection
                ?.crypto
                ?.open(Lane.CLIENT_AUDIO, frame.copyOfRange(Int.SIZE_BYTES, frame.size))
                ?.let(LinkMessage::decode) as? LinkMessage.Audio
        val samples = audio?.let { a -> connection.codec?.takeIf { it.id == a.codec }?.decode(a.payload) }
        if (device != null && audio != null && samples != null) {
            val kind = if (audio.kind == StreamKinds.MEDIA) StreamKind.MEDIA else StreamKind.VOICE
            hub.push(device.clientId, device.deviceName, audio.sequence, samples, kind)
        }
    }

    /** The patient tapped Allow. */
    fun allow(clientId: String) {
        val connection = connections.firstOrNull { it.device?.clientId == clientId && !it.welcomed } ?: return
        connection.device?.let(approved::approve)
        welcome(connection)
    }

    /** The patient tapped Don't allow. */
    fun deny(clientId: String) {
        connections.filter { it.device?.clientId == clientId && !it.welcomed }.forEach {
            it.send(LinkMessage.Denied)
            it.channel.close()
        }
    }

    /** Disconnects a companion (for example after the helper removes it). */
    fun disconnect(clientId: String) {
        connections.filter { it.device?.clientId == clientId }.forEach {
            it.send(LinkMessage.Bye)
            it.channel.close()
        }
    }

    /** Starts a new SOS (or updates its location) and sends it to every connected device. */
    fun sendSos(
        id: Long,
        location: GeoPoint?,
    ) {
        val current = sos
        val responders = if (current?.id == id) current.responders else emptyList()
        publish(SosAlert(id, active = true, location = location, responders = responders))
    }

    /** The patient is safe: tells every connected device, then forgets the SOS. */
    fun endSos() {
        val current = sos ?: return
        publish(current.copy(active = false))
        sos = null
        _sosState.value = null
    }

    private fun publish(alert: SosAlert) {
        sos = alert
        _sosState.value = alert
        connections.filter { it.welcomed }.forEach { it.send(LinkMessage.Sos(alert)) }
    }

    private fun onSosReply(
        connection: Connection,
        reply: LinkMessage.SosReply,
    ) {
        val current = sos ?: return
        val name = connection.device?.deviceName ?: return
        if (!connection.welcomed || reply.id != current.id || reply.reply != SosReplies.ON_MY_WAY) return
        if (name !in current.responders) publish(current.copy(responders = current.responders + name))
    }

    fun closeAll() {
        connections.toList().forEach { it.channel.close() }
    }

    private fun welcome(connection: Connection) {
        val hello = connection.hello ?: return
        val device = connection.device ?: return
        // The companion lists codecs in the order that suits its radio (it may be behind a relay).
        val codec =
            hello.codecs.firstOrNull { it in codecs } ?: run {
                connection.send(LinkMessage.Denied)
                connection.channel.close()
                return
            }
        var tag: Int
        do {
            tag = random.nextInt()
        } while (byTag.putIfAbsent(tag, connection) != null)
        connection.tag = tag
        connection.codec = codecs.getValue(codec)()
        connection.welcomed = true
        _pending.update { list -> list.filterNot { it.clientId == device.clientId } }
        _connected.update { list -> list.filterNot { it.clientId == device.clientId } + device }
        connection.send(LinkMessage.Welcome(codec, tag))
        sos?.let { connection.send(LinkMessage.Sos(it)) }
    }

    private inner class Connection(
        val channel: LinkChannel,
    ) : LinkChannel.Receiver {
        @Volatile var crypto: LinkCrypto? = null

        @Volatile var hello: LinkMessage.Hello? = null

        @Volatile var welcomed = false

        @Volatile var tag: Int? = null

        @Volatile var codec: FrameCodec? = null

        val device: CompanionDevice? get() = hello?.let { CompanionDevice(it.clientId, it.deviceName, it.deviceKind) }

        fun send(message: LinkMessage) {
            val c = crypto ?: return
            runCatching { channel.sendControl(c.seal(Lane.HOST_CONTROL, LinkMessage.encode(message))) }
        }

        override fun onControl(frame: ByteArray) {
            val c = crypto
            if (c == null) {
                // The first frame is the companion's random salt for this connection's keys.
                if (frame.size == LinkCrypto.SALT_BYTES) crypto = LinkCrypto(code.secret, frame) else channel.close()
                return
            }
            when (val message = c.open(Lane.CLIENT_CONTROL, frame)?.let(LinkMessage::decode)) {
                is LinkMessage.Hello -> onHello(message)
                is LinkMessage.SosReply -> onSosReply(this, message)
                LinkMessage.Bye -> channel.close()
                else -> Unit
            }
        }

        private fun onHello(message: LinkMessage.Hello) {
            if (hello != null || message.version != JoinCode.VERSION || message.clientId.isBlank()) return
            hello = message
            val device = device ?: return
            if (approved.isApproved(device.clientId)) {
                welcome(this)
            } else {
                _pending.update { list -> list.filterNot { it.clientId == device.clientId } + device }
                send(LinkMessage.Pending)
            }
        }

        override fun onAudio(frame: ByteArray) = this@LinkHost.onAudio(frame)

        override fun onClosed() {
            connections -= this
            (codec as? AutoCloseable)?.let { runCatching { it.close() } }
            tag?.let { byTag.remove(it) }
            val device = device ?: return
            _pending.update { list -> list.filterNot { it.clientId == device.clientId } }
            if (connections.none { it.device?.clientId == device.clientId && it.welcomed }) {
                _connected.update { list -> list.filterNot { it.clientId == device.clientId } }
                hub.remove(device.clientId)
            }
        }
    }
}
