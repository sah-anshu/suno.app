// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.link

import au.remotiq.suno.audiograph.RemoteAudioHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin

class SosAndRelayTest {
    private val frame = RemoteAudioHub.FRAME_SAMPLES
    private val codecs: Map<Int, () -> FrameCodec> = mapOf(CodecIds.PCM16 to { PcmCodec(frame) })
    private val code = JoinCode.generate("Dadi")
    private val hub = RemoteAudioHub()
    private val approved = mutableSetOf<String>()
    private val host =
        LinkHost(
            code,
            hub,
            object : ApprovedCompanions {
                override fun isApproved(clientId: String) = clientId in approved

                override fun approve(device: CompanionDevice) {
                    approved += device.clientId
                }
            },
            codecs,
        )

    private fun join(
        id: String,
        name: String,
    ): LinkClient {
        val link = InMemoryLink()
        host.onConnection(link.host)
        return LinkClient(code, id, name, codecs).also {
            it.connect(link.client)
            host.allow(id)
        }
    }

    private fun tone(seq: Long) =
        ShortArray(frame) { (8000 * sin(2 * PI * 1000.0 * (seq * frame + it) / 16_000)).toInt().toShort() }

    private fun heard(): Boolean {
        var any = false
        repeat(40) {
            val out = FloatArray(192)
            if (hub.read(out, 192, 48_000) && out.any { it != 0f }) any = true
        }
        return any
    }

    @Test
    fun `an SOS reaches every phone, answers are shared, and the end is announced`() {
        val rahul = join("rahul", "Rahul")
        val nana = join("nana", "Nana")
        val place = GeoPoint(28.6139, 77.2090, 12f)

        host.sendSos(7, place)
        assertEquals(SosAlert(7, true, place), rahul.sos.value)
        assertEquals(place, nana.sos.value?.location)

        rahul.replyOnMyWay()
        assertEquals(listOf("Rahul"), nana.sos.value?.responders, "others see who is coming")
        assertEquals(listOf("Rahul"), host.sosState.value?.responders)
        rahul.replyOnMyWay()
        assertEquals(listOf("Rahul"), host.sosState.value?.responders, "counted once")

        host.endSos()
        assertFalse(nana.sos.value!!.active)
        assertNull(host.sosState.value)
    }

    @Test
    fun `a phone that connects during an SOS is told at once, and a new SOS starts with no answers`() {
        val rahul = join("rahul", "Rahul")
        host.sendSos(8, null)
        val late = join("late", "Late")
        assertEquals(8L, late.sos.value?.id)
        rahul.replyOnMyWay()
        assertEquals(listOf("Rahul"), late.sos.value?.responders)

        host.endSos()
        host.sendSos(9, null)
        assertTrue(
            host.sosState.value!!
                .responders
                .isEmpty(),
        )
        assertEquals(9L, rahul.sos.value?.id)
    }

    @Test
    fun `nobody but the patient's phone can start an SOS`() {
        assertEquals(null, LinkMessage.decode(byteArrayOf(9, 1)), "a truncated SOS is rejected")
        val alert = SosAlert(3, true, GeoPoint(1.0, 2.0, 3f), listOf("A", "B"))
        assertEquals(LinkMessage.Sos(alert), LinkMessage.decode(LinkMessage.encode(LinkMessage.Sos(alert))))
    }

    @Test
    fun `a phone out of range joins through a relay, is heard, and gets the SOS`() {
        val relay =
            LinkRelay(CoroutineScope(Dispatchers.Unconfined), {
                InMemoryLink().also { host.onConnection(it.host) }.client
            })
        val far = InMemoryLink()
        relay.onConnection(far.host)
        val client = LinkClient(code, "far", "Far phone", codecs)
        client.connect(far.client)

        assertEquals(ClientState.WAITING_FOR_APPROVAL, client.state.value, "the patient still decides")
        host.allow("far")
        assertEquals(ClientState.CONNECTED, client.state.value)
        assertEquals(1, relay.active)

        (0L until 12L).forEach { client.sendFrame(tone(it)) }
        assertTrue(heard())

        host.sendSos(11, null)
        assertEquals(11L, client.sos.value?.id)
        client.replyOnMyWay()
        assertEquals(listOf("Far phone"), host.sosState.value?.responders)

        client.leave()
        assertEquals(0, relay.active)
        assertTrue(host.connected.value.isEmpty())
    }

    @Test
    fun `a relay that cannot reach the patient drops the far phone`() {
        val relay = LinkRelay(CoroutineScope(Dispatchers.Unconfined), { null })
        val far = InMemoryLink()
        val client = LinkClient(code, "far", "Far", codecs)
        client.connect(far.client)
        relay.onConnection(far.host)
        assertEquals(ClientState.CLOSED, client.state.value)
        assertEquals(0, relay.active)
    }
}
