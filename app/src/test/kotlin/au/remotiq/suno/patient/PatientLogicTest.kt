// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.patient

import au.remotiq.suno.common.AudioLimits
import au.remotiq.suno.core.audio.speech.EnhancementOptions
import au.remotiq.suno.core.audio.speech.NoiseStrength
import au.remotiq.suno.data.PatientSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PatientLogicTest {
    @Test
    fun `every volume step is inside the audio limits and increases`() {
        val gains = (1..PatientSettings.VOLUME_STEPS).map(VolumeSteps::gainDb)
        assertEquals(gains.sorted(), gains)
        gains.forEach { assertTrue(it in AudioLimits.MIN_MASTER_GAIN_DB..AudioLimits.MAX_MASTER_GAIN_CAP_DB) }
    }

    @Test
    fun `night and the helper cap both limit the volume step`() {
        val loud = PatientSettings(volumeStep = 6)
        assertEquals(3, VolumeSteps.effectiveStep(loud, Place.NIGHT))
        assertEquals(6, VolumeSteps.effectiveStep(loud, Place.HOME))
        assertEquals(4, VolumeSteps.effectiveStep(loud.copy(maxVolumeStep = 4), Place.HOME))
    }

    @Test
    fun `traffic cleans more than home and every place turns noise reduction on`() {
        val base = EnhancementOptions()
        Place.entries.forEach { assertNotEquals(NoiseStrength.OFF, it.applyTo(base).noiseReduction) }
        assertTrue(
            Place.TRAFFIC
                .applyTo(base)
                .noiseReduction.ordinal >
                Place.HOME
                    .applyTo(base)
                    .noiseReduction.ordinal,
        )
        assertEquals(Place.HOME, Place.fromName("unknown"))
    }

    @Test
    fun `pin hashes verify and never contain the pin`() {
        val stored = PinCode.hash("2580")
        assertTrue(PinCode.matches("2580", stored))
        assertFalse(PinCode.matches("2581", stored))
        assertFalse(stored.contains("2580"))
        assertNotEquals(stored, PinCode.hash("2580"), "each hash uses a new salt")
        assertFalse(PinCode.matches("2580", null))
        assertFalse(PinCode.isValid("12a4"))
    }
}
