// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.phone

import au.remotiq.suno.data.CallSettings
import au.remotiq.suno.data.Favourite
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CallRulesTest {
    private val people = listOf(Favourite("Rahul", "Son", "+919876543210"), Favourite("Clinic", "", "0112345678"))

    @Test
    fun `numbers match with or without country code and spacing`() {
        assertTrue(PhoneNumbers.same("+91 98765 43210", "098765-43210"))
        assertFalse(PhoneNumbers.same("+919876543210", "+919876543211"))
        assertTrue(PhoneNumbers.same("112", "112"))
        assertFalse(PhoneNumbers.same("112", "100"))
        assertFalse(PhoneNumbers.same("", "112"))
    }

    @Test
    fun `people come first, then contacts, else unknown`() {
        val rahul = CallRules.identify("09876543210", people, contactName = "R. Kumar")
        assertEquals("Rahul", rahul.name)
        assertEquals("Son", rahul.relationship)
        assertTrue(rahul.inPeople)
        assertNull(CallRules.identify("0112345678", people, null).relationship, "blank relationship is not shown")
        assertEquals("Neighbour", CallRules.identify("5550000000", people, "Neighbour").name)
        assertFalse(CallRules.identify("5550000000", people, null).known)
    }

    @Test
    fun `contacts only blocks unknown numbers, auto-answer only for known callers`() {
        val strict = CallSettings(contactsOnly = true, autoAnswer = true)
        val stranger = CallRules.identify("5551234567", people, null)
        val son = CallRules.identify("+919876543210", people, null)
        assertTrue(CallRules.shouldBlock(stranger, strict, sosActive = false))
        assertFalse(CallRules.shouldBlock(stranger, strict, sosActive = true), "during SOS everyone gets through")
        assertFalse(CallRules.shouldBlock(stranger, CallSettings(), sosActive = false))
        assertFalse(CallRules.shouldBlock(son, strict, sosActive = false))
        assertTrue(CallRules.shouldAutoAnswer(son, strict))
        assertFalse(CallRules.shouldAutoAnswer(stranger, strict))
        assertFalse(CallRules.shouldAutoAnswer(son, CallSettings()))
    }
}
