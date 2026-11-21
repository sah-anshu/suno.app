// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.sos

import au.remotiq.suno.data.SosSettings
import au.remotiq.suno.link.CompanionDevice
import au.remotiq.suno.link.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SosControllerTest {
    private class FakeActions(
        val smsAllowed: Boolean,
        val place: GeoPoint?,
        val speaker: Boolean = true,
        val helperAnswers: Boolean? = true,
        override val canCallEmergencyDirectly: Boolean = true,
    ) : SosActions {
        val log = mutableListOf<String>()

        override fun sendTexts(
            numbers: List<String>,
            body: String,
        ): Boolean {
            log += "texts:${numbers.size}:${if (smsAllowed) "sent" else "refused"}"
            return smsAllowed
        }

        override fun openTexts(
            numbers: List<String>,
            body: String,
        ) {
            log += "open-texts"
        }

        override fun call(number: String) {
            log += "call:$number"
        }

        override suspend fun callAndWait(number: String): Boolean? {
            log += "call:$number"
            return helperAnswers
        }

        override fun callEmergency(number: String) {
            log += "emergency:$number"
        }

        override suspend fun locate() = place

        override fun alarm(on: Boolean): Boolean {
            log += "alarm:$on"
            return on && speaker
        }

        override fun stopListening() {
            log += "stop-listening"
        }

        override fun keepRunning(on: Boolean) {
            log += "keep-running:$on"
        }
    }

    private class FakeLink : SosLink {
        override val connected = MutableStateFlow(listOf(CompanionDevice("r", "Rahul"), CompanionDevice("n", "Nana")))
        override val sosResponders = MutableStateFlow<List<String>>(emptyList())
        val sent = mutableListOf<GeoPoint?>()
        var ended = false

        override fun sendSos(
            id: Long,
            location: GeoPoint?,
        ) {
            sent += location
        }

        override fun endSos() {
            ended = true
        }
    }

    private val settings =
        SosSettings(helperName = "Rahul", helperNumber = "+919876543210", textNumbers = listOf("111", "222"))
    private val place = GeoPoint(28.6, 77.2, 15f)

    private fun controller(
        actions: SosActions,
        link: SosLink,
        s: SosSettings = settings,
        escalate: Boolean = true,
    ) = SosController(CoroutineScope(Dispatchers.Unconfined), {
        SosStart(s, "Dadi", escalate)
    }, actions, link, clock = { 42L })

    @Test
    fun `nothing happens until the patient says yes, and no goes straight back`() {
        val actions = FakeActions(smsAllowed = true, place = place)
        val link = FakeLink()
        val sos = controller(actions, link)
        sos.ask()
        assertEquals(SosPhase.ASKING, sos.state.value.phase)
        sos.cancel()
        assertEquals(SosPhase.IDLE, sos.state.value.phase)
        assertTrue(actions.log.isEmpty())
        assertTrue(link.sent.isEmpty())
    }

    @Test
    fun `yes alerts the mesh, calls the helper, shares the place and texts`() {
        val actions = FakeActions(smsAllowed = true, place = place)
        val link = FakeLink()
        val sos = controller(actions, link)
        sos.ask()
        sos.confirm()
        val s = sos.state.value
        assertEquals(SosPhase.ACTIVE, s.phase)
        assertEquals(listOf(null, place), link.sent, "alerted at once, then again with the place")
        assertEquals(2, s.alerted)
        assertTrue("call:+919876543210" in actions.log)
        assertTrue("stop-listening" in actions.log, "no amplified sound during SOS")
        assertEquals(2, s.textsSent)
        assertFalse(s.textsNeedTap)
        assertFalse(s.alarmOn, "alarm is off unless the helper chose otherwise")

        link.sosResponders.value = listOf("Rahul")
        assertEquals(listOf("Rahul"), sos.state.value.responders)

        assertEquals(true, s.helperAnswered)
        assertFalse("emergency:112" in actions.log, "the helper answered, so no emergency call")
        sos.callEmergency()
        assertTrue("emergency:112" in actions.log)
        sos.end()
        assertTrue(link.ended)
        assertEquals(SosPhase.IDLE, sos.state.value.phase)
        assertEquals("keep-running:false", actions.log.last())
    }

    @Test
    fun `without SMS permission the patient taps to open the messaging app`() {
        val actions = FakeActions(smsAllowed = false, place = null)
        val sos = controller(actions, FakeLink())
        sos.ask()
        sos.confirm()
        assertTrue(sos.state.value.textsNeedTap)
        assertEquals(0, sos.state.value.textsSent)
        sos.openTexts()
        assertTrue("open-texts" in actions.log)
    }

    @Test
    fun `the alarm stays silent when there is no speaker, and can start on its own`() {
        val noSpeaker = FakeActions(smsAllowed = true, place = null, speaker = false)
        val sos = controller(noSpeaker, FakeLink(), settings.copy(alarmOnStart = true))
        sos.ask()
        sos.confirm()
        assertTrue("alarm:true" in noSpeaker.log)
        assertFalse(sos.state.value.alarmOn)

        val withSpeaker = FakeActions(smsAllowed = true, place = null)
        val other = controller(withSpeaker, FakeLink())
        other.ask()
        other.confirm()
        other.setAlarm(true)
        assertTrue(other.state.value.alarmOn)
        other.setAlarm(false)
        assertFalse(other.state.value.alarmOn)
    }

    @Test
    fun `text names the patient and the place, or says the place is unknown`() {
        val text = SosText.build("Dadi", place)
        assertTrue(text.startsWith("SOS from Dadi."))
        assertTrue("28.60000,77.20000" in text)
        assertTrue("Location not available" in SosText.build("", null))
    }

    @Test
    fun `if the helper does not answer, Suno calls the emergency number when allowed`() {
        val noAnswer = FakeActions(smsAllowed = true, place = null, helperAnswers = false)
        val sos = controller(noAnswer, FakeLink())
        sos.ask()
        sos.confirm()
        assertEquals(false, sos.state.value.helperAnswered)
        assertTrue(sos.state.value.emergencyCalled)
        assertTrue("emergency:112" in noAnswer.log)
    }

    @Test
    fun `no automatic emergency call when the helper turned it off or Suno cannot tell`() {
        val off = FakeActions(smsAllowed = true, place = null, helperAnswers = false)
        controller(off, FakeLink(), escalate = false).apply {
            ask()
            confirm()
        }
        assertFalse("emergency:112" in off.log)

        val unknown =
            FakeActions(smsAllowed = true, place = null, helperAnswers = null, canCallEmergencyDirectly = false)
        val sos = controller(unknown, FakeLink())
        sos.ask()
        sos.confirm()
        assertFalse(sos.state.value.emergencyCalled)
        assertFalse("emergency:112" in unknown.log)
    }

    @Test
    fun `with no helper number, Suno calls the emergency number itself when it is the phone app`() {
        val actions = FakeActions(smsAllowed = true, place = null)
        val sos = controller(actions, FakeLink(), SosSettings())
        sos.ask()
        sos.confirm()
        assertTrue("emergency:112" in actions.log)
    }
}
