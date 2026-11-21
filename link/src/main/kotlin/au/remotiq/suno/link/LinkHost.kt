// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.link

import au.remotiq.suno.audiograph.RemoteAudioHub
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
            hub.push(device.clientId, device.deviceName, audio.sequence, samples)
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

    fun closeAll() {
        connections.toList().forEach { it.channel.close() }
    }

    private fun welcome(connection: Connection) {
        val hello = connection.hello ?: return
        val device = connection.device ?: return
        val codec =
            listOf(connection.channel.preferredCodec, CodecIds.PCM16)
                .firstOrNull { it in hello.codecs && it in codecs } ?: run {
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
    }

    private inner class Connection(
        val channel: LinkChannel,
    ) : LinkChannel.Receiver {
        @Volatile var crypto: LinkCrypto? = null

        @Volatile var hello: LinkMessage.Hello? = null

        @Volatile var welcomed = false

        @Volatile var tag: Int? = null

        @Volatile var codec: FrameCodec? = null

        val device: CompanionDevice? get() = hello?.let { CompanionDevice(it.clientId, it.deviceName) }

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
