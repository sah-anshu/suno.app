// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.audiogram

import au.remotiq.suno.common.AudioEar
import au.remotiq.suno.common.AudioLimits
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReadyMadeProfileTest {
    @Test
    fun `every profile covers both ears at the six octave frequencies`() {
        ReadyMadeProfile.entries.forEach { profile ->
            AudioEar.entries.forEach { ear ->
                assertEquals(ReadyMadeProfile.FREQUENCIES, profile.audiogram.frequenciesFor(ear), "${profile.id} $ear")
            }
        }
    }

    @Test
    fun `thresholds slope downward like age-related loss`() {
        ReadyMadeProfile.entries.forEach { profile ->
            val levels = ReadyMadeProfile.FREQUENCIES.map { profile.audiogram.thresholdAt(AudioEar.RIGHT, it)!!.value }
            assertEquals(levels.sorted(), levels, "${profile.id} should not get better at higher pitches")
        }
    }

    @Test
    fun `child-safe never allows more master gain than the others`() {
        val others = ReadyMadeProfile.entries.filter { it != ReadyMadeProfile.CHILD_SAFE }
        others.forEach { assertTrue(ReadyMadeProfile.CHILD_SAFE.masterGainCapDb <= it.masterGainCapDb) }
        ReadyMadeProfile.entries.forEach {
            assertTrue(it.masterGainCapDb in AudioLimits.MIN_MASTER_GAIN_DB..AudioLimits.MAX_MASTER_GAIN_CAP_DB)
        }
    }

    @Test
    fun `ids round-trip and unknown ids give null`() {
        ReadyMadeProfile.entries.forEach { assertEquals(it, ReadyMadeProfile.fromId(it.id)) }
        assertNull(ReadyMadeProfile.fromId("not-a-profile"))
        assertNotNull(ReadyMadeProfile.DEFAULT)
    }
}
