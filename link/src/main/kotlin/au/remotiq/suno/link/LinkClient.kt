// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.link

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.security.SecureRandom

enum class ClientState { IDLE, CONNECTING, WAITING_FOR_APPROVAL, CONNECTED, DENIED, CLOSED }

/**
 * The companion's side of the link: asks to join, waits for the patient's phone to allow it, then
 * sends 20 ms voice frames. Sends clean audio; the patient's phone applies the patient's audiogram.
 */
class LinkClient(
    private val code: JoinCode,
    private val clientId: String,
    private val deviceName: String,
    private val codecs: Map<Int, () -> FrameCodec>,
    private val random: SecureRandom = SecureRandom(),
) : LinkChannel.Receiver {
    private val _state = MutableStateFlow(ClientState.IDLE)
    val state: StateFlow<ClientState> = _state.asStateFlow()

    @Volatile private var channel: LinkChannel? = null

    @Volatile private var crypto: LinkCrypto? = null

    @Volatile private var tag = 0

    @Volatile private var codec: FrameCodec? = null
    private var sequence = 0L

    /** Name of the codec in use, for status display. */
    val codecId: Int? get() = codec?.id

    fun connect(channel: LinkChannel) {
        val salt = ByteArray(LinkCrypto.SALT_BYTES).also(random::nextBytes)
        this.channel = channel
        crypto = LinkCrypto(code.secret, salt)
        _state.value = ClientState.CONNECTING
        channel.start(this)
        channel.sendControl(salt)
        send(LinkMessage.Hello(JoinCode.VERSION, clientId, deviceName, codecs.keys.toList()))
    }

    /** Sends one 20 ms, 16 kHz frame. Ignored unless connected. */
    fun sendFrame(pcm: ShortArray) {
        val c = crypto ?: return
        val active = codec ?: return
        val ch = channel ?: return
        if (_state.value != ClientState.CONNECTED) return
        val message = LinkMessage.Audio(sequence++, active.id, active.encode(pcm))
        val sealed = c.seal(Lane.CLIENT_AUDIO, LinkMessage.encode(message))
        runCatching {
            ch.sendAudio(
                ByteBuffer
                    .allocate(Int.SIZE_BYTES + sealed.size)
                    .putInt(tag)
                    .put(sealed)
                    .array(),
            )
        }
    }

    fun leave() {
        send(LinkMessage.Bye)
        channel?.close()
        _state.value = ClientState.CLOSED
    }

    override fun onControl(frame: ByteArray) {
        when (val message = crypto?.open(Lane.HOST_CONTROL, frame)?.let(LinkMessage::decode)) {
            LinkMessage.Pending -> _state.value = ClientState.WAITING_FOR_APPROVAL
            is LinkMessage.Welcome -> {
                codec = codecs[message.codec]?.invoke()
                tag = message.audioTag
                _state.value = if (codec != null) ClientState.CONNECTED else ClientState.CLOSED
            }
            LinkMessage.Denied -> {
                _state.value = ClientState.DENIED
                channel?.close()
            }
            LinkMessage.Bye -> channel?.close()
            else -> Unit
        }
    }

    override fun onAudio(frame: ByteArray) = Unit

    override fun onClosed() {
        (codec as? AutoCloseable)?.let { runCatching { it.close() } }
        if (_state.value != ClientState.DENIED) _state.value = ClientState.CLOSED
    }

    private fun send(message: LinkMessage) {
        val c = crypto ?: return
        runCatching { channel?.sendControl(c.seal(Lane.CLIENT_CONTROL, LinkMessage.encode(message))) }
    }
}
