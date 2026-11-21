// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.link

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Mesh relay on a companion phone that is connected to the patient. A phone out of the patient's
 * range connects here instead, and each of its connections is piped to a new connection to the
 * patient's phone. The relay forwards bytes it cannot read: keys are agreed end to end between the
 * far phone and the patient's phone, and the patient still allows or refuses it. One relay level
 * only (at most two hops), so a relayed phone never relays.
 */
class LinkRelay(
    private val scope: CoroutineScope,
    /** Opens a new connection to the patient's phone, or returns null if it cannot. */
    private val upstream: suspend () -> LinkChannel?,
    private val maxPipes: Int = MAX_PIPES,
) : LinkServer.Listener {
    private val pipes = ConcurrentHashMap.newKeySet<Pipe>()

    /** Phones being relayed right now. */
    val active: Int get() = pipes.size

    override fun onConnection(channel: LinkChannel) {
        if (pipes.size >= maxPipes) {
            channel.close()
            return
        }
        val pipe = Pipe(channel)
        pipes += pipe
        pipe.listen()
        scope.launch { pipe.connect() }
    }

    /** Audio datagrams carry the patient's tag, so any open pipe can carry them. */
    override fun onAudio(frame: ByteArray) {
        pipes.firstNotNullOfOrNull { it.up }?.let { runCatching { it.sendAudio(frame) } }
    }

    fun closeAll() {
        pipes.toList().forEach { it.close() }
    }

    private inner class Pipe(
        private val down: LinkChannel,
    ) {
        @Volatile var up: LinkChannel? = null
        private val early = ArrayList<ByteArray>()
        private var closed = false

        fun listen() {
            down.start(
                object : LinkChannel.Receiver {
                    override fun onControl(frame: ByteArray) {
                        val target =
                            synchronized(early) {
                                up ?: run {
                                    // The salt and Hello arrive before the patient's side is open.
                                    if (early.size < MAX_EARLY) early += frame
                                    null
                                }
                            }
                        target?.let { runCatching { it.sendControl(frame) }.onFailure { close() } }
                    }

                    override fun onAudio(frame: ByteArray) {
                        up?.let { runCatching { it.sendAudio(frame) } }
                    }

                    override fun onClosed() = close()
                },
            )
        }

        suspend fun connect() {
            val channel = runCatching { upstream() }.getOrNull()
            if (channel == null) {
                close()
                return
            }
            channel.start(
                object : LinkChannel.Receiver {
                    override fun onControl(frame: ByteArray) {
                        runCatching { down.sendControl(frame) }.onFailure { close() }
                    }

                    override fun onAudio(frame: ByteArray) {
                        runCatching { down.sendAudio(frame) }
                    }

                    override fun onClosed() = close()
                },
            )
            val queued =
                synchronized(early) {
                    if (closed) {
                        null
                    } else {
                        early.toList().also {
                            early.clear()
                            up = channel
                        }
                    }
                }
            if (queued == null) {
                channel.close()
                return
            }
            queued.forEach { runCatching { channel.sendControl(it) } }
        }

        fun close() {
            val wasOpen =
                synchronized(early) {
                    if (closed) return
                    closed = true
                    true
                }
            if (wasOpen) {
                pipes -= this
                runCatching { down.close() }
                up?.let { runCatching { it.close() } }
            }
        }
    }

    private companion object {
        const val MAX_PIPES = 4
        const val MAX_EARLY = 8
    }
}
