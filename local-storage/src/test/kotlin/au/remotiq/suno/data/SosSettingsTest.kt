// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class SosSettingsTest {
    @Test
    fun `numbers are cleaned, duplicates removed and the emergency number never empty`() {
        val s =
            SosSettings(
                helperName = "  Rahul ",
                helperNumber = "+91 98765-43210",
                emergencyNumber = " ",
                textNumbers = listOf("98765 43210", "9876543210", "abc", "+44 (20) 7946 0000"),
            ).normalized()
        assertEquals("Rahul", s.helperName)
        assertEquals("+919876543210", s.helperNumber)
        assertEquals("112", s.emergencyNumber)
        assertEquals(listOf("9876543210", "+442079460000"), s.textNumbers)
    }

    @Test
    fun `typed list splits on commas and new lines`() {
        assertEquals(listOf("111", "+222", "333"), SosSettings.parseNumbers("111, +222\n333;"))
        assertFalse(SosSettings().hasHelper)
    }
}
