// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.link

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Patient side over IP (used on the Wi-Fi Direct group): TCP for control, UDP on the same port
 * for audio. Only local addresses are involved; nothing is sent to the internet.
 */
class SocketLinkServer(
    private val bindAddress: InetAddress? = null,
    requestedPort: Int = 0,
) : LinkServer {
    private val tcp = ServerSocket().apply { bind(InetSocketAddress(bindAddress, requestedPort)) }
    private val udp = DatagramSocket(InetSocketAddress(bindAddress, tcp.localPort))

    @Volatile private var running = false

    val port: Int get() = tcp.localPort

    override fun start(listener: LinkServer.Listener) {
        running = true
        thread(name = "suno-link-accept", isDaemon = true) {
            while (running) {
                val socket = runCatching { tcp.accept() }.getOrNull() ?: break
                socket.tcpNoDelay = true
                listener.onConnection(StreamChannel(socket.getInputStream(), socket.getOutputStream(), socket::close))
            }
        }
        thread(name = "suno-link-audio", isDaemon = true) {
            val buffer = ByteArray(MAX_DATAGRAM)
            while (running) {
                val packet = DatagramPacket(buffer, buffer.size)
                runCatching { udp.receive(packet) }.getOrNull() ?: break
                listener.onAudio(packet.data.copyOfRange(packet.offset, packet.offset + packet.length))
            }
        }
    }

    override fun stop() {
        running = false
        runCatching { tcp.close() }
        runCatching { udp.close() }
    }

    private companion object {
        const val MAX_DATAGRAM = 4096
    }
}

/** Companion side over IP: connects to the patient's phone at [host]:[port]. */
object SocketLinkConnector {
    fun connect(
        host: InetAddress,
        port: Int,
        timeoutMs: Int = 5_000,
    ): LinkChannel {
        val socket = Socket()
        socket.connect(InetSocketAddress(host, port), timeoutMs)
        socket.tcpNoDelay = true
        val udp = DatagramSocket()
        udp.connect(InetSocketAddress(host, port))
        return StreamChannel(
            input = socket.getInputStream(),
            output = socket.getOutputStream(),
            onClose = {
                runCatching { socket.close() }
                runCatching { udp.close() }
            },
            audioSender = { frame -> udp.send(DatagramPacket(frame, frame.size)) },
        )
    }
}
