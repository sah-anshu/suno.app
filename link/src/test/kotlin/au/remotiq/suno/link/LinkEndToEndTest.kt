// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.link

import au.remotiq.suno.audiograph.RemoteAudioHub
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress
import kotlin.math.PI
import kotlin.math.sin

class LinkEndToEndTest {
    private val frame = RemoteAudioHub.FRAME_SAMPLES
    private val codecs: Map<Int, () -> FrameCodec> = mapOf(CodecIds.PCM16 to { PcmCodec(frame) })

    private class Store : ApprovedCompanions {
        val ids = mutableSetOf<String>()

        override fun isApproved(clientId: String) = clientId in ids

        override fun approve(device: CompanionDevice) {
            ids += device.clientId
        }
    }

    private fun tone(seq: Long) =
        ShortArray(frame) {
            (8000 * sin(2 * PI * 1000.0 * (seq * frame + it) / 16_000)).toInt().toShort()
        }

    /** Reads the hub like the audio thread does and reports whether any voice came out. */
    private fun heard(hub: RemoteAudioHub): Boolean {
        var any = false
        repeat(40) {
            val out = FloatArray(192)
            if (hub.read(out, 192, 48_000) && out.any { it != 0f }) any = true
        }
        return any
    }

    @Test
    fun `a new phone waits for approval, then its voice reaches the patient`() {
        val code = JoinCode.generate("Dadi")
        val hub = RemoteAudioHub()
        val store = Store()
        val host = LinkHost(code, hub, store, codecs)
        val link = InMemoryLink()
        host.onConnection(link.host)
        val client = LinkClient(code, "rahul-1", "Rahul's phone", codecs)
        client.connect(link.client)

        assertEquals(ClientState.WAITING_FOR_APPROVAL, client.state.value)
        assertEquals(listOf("rahul-1"), host.pending.value.map { it.clientId })
        (0L until 5L).forEach { client.sendFrame(tone(it)) }
        assertFalse(heard(hub), "nothing is played before the patient allows it")

        host.allow("rahul-1")
        assertEquals(ClientState.CONNECTED, client.state.value)
        assertTrue(host.pending.value.isEmpty())
        (5L until 15L).forEach { client.sendFrame(tone(it)) }
        assertTrue(heard(hub))
        assertTrue("rahul-1" in store.ids, "remembered for next time")
    }

    @Test
    fun `refused phones get nothing and remembered phones rejoin without asking`() {
        val code = JoinCode.generate("Dadi")
        val hub = RemoteAudioHub()
        val store = Store().apply { ids += "known" }
        val host = LinkHost(code, hub, store, codecs)

        val strangerLink = InMemoryLink()
        host.onConnection(strangerLink.host)
        val stranger = LinkClient(code, "stranger", "Unknown", codecs)
        stranger.connect(strangerLink.client)
        host.deny("stranger")
        assertEquals(ClientState.DENIED, stranger.state.value)

        val knownLink = InMemoryLink()
        host.onConnection(knownLink.host)
        val known = LinkClient(code, "known", "Nana", codecs)
        known.connect(knownLink.client)
        assertEquals(ClientState.CONNECTED, known.state.value)
        assertEquals(listOf("known"), host.connected.value.map { it.clientId })

        known.leave()
        assertTrue(host.connected.value.isEmpty())
    }

    @Test
    fun `a phone with the wrong join code cannot join`() {
        val hub = RemoteAudioHub()
        val host = LinkHost(JoinCode.generate("Dadi"), hub, Store(), codecs)
        val link = InMemoryLink()
        host.onConnection(link.host)
        val impostor = LinkClient(JoinCode.generate("Someone else"), "x", "X", codecs)
        impostor.connect(link.client)
        assertTrue(host.pending.value.isEmpty())
        assertEquals(ClientState.CONNECTING, impostor.state.value)
    }

    @Test
    fun `voice travels over real local sockets (TCP control, UDP audio)`() {
        val code = JoinCode.generate("Dadi")
        val hub = RemoteAudioHub()
        val host = LinkHost(code, hub, Store(), codecs)
        val server = SocketLinkServer(InetAddress.getLoopbackAddress())
        server.start(host)
        try {
            val client = LinkClient(code, "rahul-2", "Rahul", codecs)
            client.connect(SocketLinkConnector.connect(InetAddress.getLoopbackAddress(), server.port))
            waitFor { host.pending.value.isNotEmpty() }
            host.allow("rahul-2")
            waitFor { client.state.value == ClientState.CONNECTED }
            (0L until 20L).forEach {
                client.sendFrame(tone(it))
                Thread.sleep(2)
            }
            waitFor { heard(hub) }
            client.leave()
            waitFor { host.connected.value.isEmpty() }
        } finally {
            server.stop()
        }
    }

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 3_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }
}
