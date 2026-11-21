// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.audiograph

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class ResamplerTest {
    @Test
    fun `16 kHz speech up-sampled to 48 kHz keeps its level and pitch`() {
        val out = Resampler(16_000, 48_000).process(sine(1_000.0, 16_000, 16_000))
        assertTrue(abs(out.size - 48_000) < 100, "length ${out.size}")
        val steady = out.copyOfRange(4_800, 43_200)
        assertEquals(0.5, level(steady, 1_000.0, 48_000), 0.02)
        assertTrue(level(steady, 15_000.0, 48_000) < 0.005, "image at 15 kHz must be suppressed")
    }

    @Test
    fun `down-sampling removes content above the new Nyquist instead of aliasing`() {
        val out = Resampler(48_000, 16_000).process(sine(12_000.0, 48_000, 48_000))
        val steady = out.copyOfRange(1_600, 14_400)
        assertTrue(level(steady, 4_000.0, 16_000) < 0.01, "12 kHz must not fold to 4 kHz")
    }

    @Test
    fun `chunked input gives the same output as one block`() {
        val input = sine(700.0, 16_000, 3_200)
        val whole = Resampler(16_000, 44_100).process(input)
        val chunked = Resampler(16_000, 44_100)
        val parts = mutableListOf<Float>()
        var i = 0
        listOf(1, 63, 320, 7, 500, 2309).forEach { n ->
            parts += chunked.process(input.copyOfRange(i, i + n)).toList()
            i += n
        }
        assertArrayEquals(whole, parts.toFloatArray(), 1e-5f)
    }

    @Test
    fun `same rate passes audio through`() {
        val input = sine(440.0, 16_000, 1_600)
        val out = Resampler(16_000, 16_000).process(input)
        val steady = out.copyOfRange(100, 1_400)
        assertEquals(rms(input.copyOfRange(100, 1_400)), rms(steady), 0.01)
    }
}
