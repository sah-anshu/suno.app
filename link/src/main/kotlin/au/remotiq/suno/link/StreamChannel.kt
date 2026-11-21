// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.link

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * A [LinkChannel] over one reliable byte stream (a TCP socket, or a Bluetooth L2CAP channel).
 * Frames are `[kind: 1][length: 4][bytes]`. Audio goes over the same stream unless [audioSender]
 * provides a faster, unreliable path (UDP on Wi-Fi Direct).
 */
class StreamChannel(
    input: InputStream,
    output: OutputStream,
    private val onClose: () -> Unit,
    override val preferredCodec: Int = CodecIds.PCM16,
    private val audioSender: ((ByteArray) -> Unit)? = null,
    private val name: String = "suno-link",
) : LinkChannel {
    private val reader = DataInputStream(input.buffered())
    private val writer = DataOutputStream(output.buffered())
    private val closed = AtomicBoolean(false)
    private var receiver: LinkChannel.Receiver? = null

    override fun sendControl(frame: ByteArray) = write(KIND_CONTROL, frame)

    override fun sendAudio(frame: ByteArray) {
        val fast = audioSender
        if (fast != null) runCatching { fast(frame) } else write(KIND_AUDIO, frame)
    }

    override fun start(receiver: LinkChannel.Receiver) {
        this.receiver = receiver
        thread(name = name, isDaemon = true) { readLoop(receiver) }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { onClose() }
        receiver?.onClosed()
    }

    private fun write(
        kind: Int,
        frame: ByteArray,
    ) {
        if (closed.get()) return
        try {
            synchronized(writer) {
                writer.writeByte(kind)
                writer.writeInt(frame.size)
                writer.write(frame)
                writer.flush()
            }
        } catch (
            @Suppress("SwallowedException") e: java.io.IOException,
        ) {
            // A failed write means the peer is gone; closing reports it through onClosed.
            close()
        }
    }

    private fun readLoop(receiver: LinkChannel.Receiver) {
        try {
            while (!closed.get()) {
                val kind = reader.readUnsignedByte()
                val size = reader.readInt()
                if (size !in 0..MAX_FRAME) break
                val frame = ByteArray(size).also(reader::readFully)
                when (kind) {
                    KIND_CONTROL -> receiver.onControl(frame)
                    KIND_AUDIO -> receiver.onAudio(frame)
                }
            }
        } catch (_: java.io.IOException) {
            // Peer went away; fall through to close.
        } finally {
            close()
        }
    }

    private companion object {
        const val KIND_CONTROL = 0
        const val KIND_AUDIO = 1
        const val MAX_FRAME = 64 * 1024
    }
}
