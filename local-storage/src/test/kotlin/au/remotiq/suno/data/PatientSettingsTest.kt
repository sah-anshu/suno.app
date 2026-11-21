// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PatientSettingsTest {
    @Test
    fun `defaults start simple and safe`() {
        val s = PatientSettings()
        assertTrue(s.picturesMode)
        assertEquals(2, s.homeLevel)
        assertTrue(s.volumeStep <= s.maxVolumeStep)
        assertNull(s.pinHash)
    }

    @Test
    fun `volume never exceeds the helper's cap`() {
        val s = PatientSettings(volumeStep = 6, maxVolumeStep = 4).normalized()
        assertEquals(4, s.volumeStep)
    }

    @Test
    fun `out-of-range values are pulled back into range`() {
        val s = PatientSettings(volumeStep = 0, maxVolumeStep = 9, homeLevel = 7).normalized()
        assertEquals(1, s.volumeStep)
        assertEquals(PatientSettings.VOLUME_STEPS, s.maxVolumeStep)
        assertEquals(PatientSettings.HOME_LEVELS, s.homeLevel)
    }
}
