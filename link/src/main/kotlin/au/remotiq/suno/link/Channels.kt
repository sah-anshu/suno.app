// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.link

/**
 * One connection between a companion and the patient's phone. Control frames are reliable and
 * ordered; audio frames may be dropped or reordered (UDP on Wi-Fi Direct).
 */
interface LinkChannel {
    /** Codec suited to this transport's bandwidth: PCM on Wi-Fi Direct, Opus on Bluetooth LE. */
    val preferredCodec: Int get() = CodecIds.PCM16

    fun sendControl(frame: ByteArray)

    fun sendAudio(frame: ByteArray)

    fun start(receiver: Receiver)

    fun close()

    interface Receiver {
        fun onControl(frame: ByteArray)

        fun onAudio(frame: ByteArray)

        fun onClosed()
    }
}

/**
 * Accepts connections on the patient's phone. Audio datagrams that arrive outside a channel go to
 * [Listener.onAudio].
 */
interface LinkServer {
    fun start(listener: Listener)

    fun stop()

    interface Listener {
        fun onConnection(channel: LinkChannel)

        fun onAudio(frame: ByteArray)
    }
}

/** Two linked in-memory channels, for tests. */
class InMemoryLink {
    private inner class End : LinkChannel {
        lateinit var peer: End
        var receiver: LinkChannel.Receiver? = null
        var closed = false

        override fun sendControl(frame: ByteArray) {
            if (!closed) peer.receiver?.onControl(frame.copyOf())
        }

        override fun sendAudio(frame: ByteArray) {
            if (!closed) peer.receiver?.onAudio(frame.copyOf())
        }

        override fun start(receiver: LinkChannel.Receiver) {
            this.receiver = receiver
        }

        override fun close() {
            if (closed) return
            closed = true
            peer.closed = true
            peer.receiver?.onClosed()
            receiver?.onClosed()
        }
    }

    private val a = End()
    private val b = End()

    init {
        a.peer = b
        b.peer = a
    }

    val client: LinkChannel get() = a
    val host: LinkChannel get() = b
}
