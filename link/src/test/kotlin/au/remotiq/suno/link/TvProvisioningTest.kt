// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.link

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TvProvisioningTest {
    @Test
    fun `a companion hands the patient's join code to the TV`() {
        val tvCode = JoinCode.generate("Living room TV", tvSetup = true)
        val patient = JoinCode.generate("Dadi")
        var received: JoinCode? = null
        val host = TvProvisioningHost(tvCode) { received = it }
        val link = InMemoryLink()
        host.onConnection(link.host)
        val client = TvProvisioningClient(tvCode, patient)
        client.send(link.client)
        assertEquals(TvProvisioningClient.State.DONE, client.state.value)
        assertEquals(patient.sessionId, received?.sessionId)
        assertTrue(JoinCode.parse(tvCode.toUri())!!.tvSetup)
    }

    @Test
    fun `a phone without the TV's code cannot set it up`() {
        val tvCode = JoinCode.generate("TV", tvSetup = true)
        var received: JoinCode? = null
        val host = TvProvisioningHost(tvCode) { received = it }
        val link = InMemoryLink()
        host.onConnection(link.host)
        TvProvisioningClient(JoinCode.generate("Other TV", tvSetup = true), JoinCode.generate("Dadi")).send(link.client)
        assertNull(received)
    }

    @Test
    fun `a TV setup code is never accepted as the patient's code`() {
        val tvCode = JoinCode.generate("TV", tvSetup = true)
        var received: JoinCode? = null
        val host = TvProvisioningHost(tvCode) { received = it }
        val link = InMemoryLink()
        host.onConnection(link.host)
        TvProvisioningClient(tvCode, JoinCode.generate("Another TV", tvSetup = true)).send(link.client)
        assertNull(received)
    }
}
