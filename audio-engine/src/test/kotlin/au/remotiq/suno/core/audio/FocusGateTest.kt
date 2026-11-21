// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.core.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class FocusGateTest {
    private fun block(value: Short = 10_000) = ShortArray(480) { value }

    @Test
    fun `open gate passes audio unchanged`() {
        val gate = FocusGate(rampSamples = 480)
        val out = block()
        gate.apply(out, paused = false)
        assertTrue(out.all { it == 10_000.toShort() })
    }

    @Test
    fun `pausing fades to silence without a jump`() {
        val gate = FocusGate(rampSamples = 480)
        val out = block()
        gate.apply(out, paused = true)
        val steps = out.toList().zipWithNext { a, b -> abs(a - b) }
        assertTrue(steps.all { it <= 30 }, "fade must be gradual")
        assertEquals(0, out.last().toInt())
        val silent = block()
        gate.apply(silent, paused = true)
        assertTrue(silent.all { it == 0.toShort() })
    }

    @Test
    fun `resuming fades back in instead of starting at full level`() {
        val gate = FocusGate(rampSamples = 960)
        gate.apply(block(), paused = true)
        gate.apply(block(), paused = true)
        val out = block()
        gate.apply(out, paused = false)
        assertTrue(out.first() < 100, "first sample after resume should be near silence, was ${out.first()}")
        assertTrue(out.last() < 10_000, "still ramping at the end of the first block")
        assertTrue(gate.level > 0.4f && gate.level < 0.6f)
    }

    @Test
    fun `gate never makes audio louder`() {
        val gate = FocusGate(rampSamples = 100)
        val out = ShortArray(1000) { (if (it % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE) }
        gate.apply(out, paused = true)
        gate.apply(out, paused = false)
        assertTrue(out.all { it in Short.MIN_VALUE..Short.MAX_VALUE })
    }
}
