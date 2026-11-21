// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.audiograph

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JitterBufferTest {
    private fun frame(value: Float) = FloatArray(4) { value }

    @Test
    fun `waits for enough frames before playing`() {
        val jb = JitterBuffer(4, targetFrames = 3)
        jb.push(0, frame(0f))
        jb.push(1, frame(1f))
        assertNull(jb.pull())
        jb.push(2, frame(2f))
        assertEquals(0f, jb.pull()!![0])
    }

    @Test
    fun `reorders frames and drops late duplicates`() {
        val jb = JitterBuffer(4, targetFrames = 2)
        jb.push(11, frame(11f))
        jb.push(10, frame(10f))
        assertEquals(10f, jb.pull()!![0])
        jb.push(10, frame(99f)) // late duplicate
        assertEquals(11f, jb.pull()!![0])
    }

    @Test
    fun `a lost frame is concealed quietly, then playback stops`() {
        val jb = JitterBuffer(4, targetFrames = 1)
        jb.push(0, frame(1f))
        assertEquals(1f, jb.pull()!![0])
        val concealed = jb.pull()
        assertNotNull(concealed)
        assertTrue(concealed!![0] in 0.01f..0.6f, "concealment repeats at lower level")
        assertNull(jb.pull(), "with nothing queued, the buffer goes quiet")
    }

    @Test
    fun `delay stays bounded when frames pile up`() {
        val jb = JitterBuffer(4, targetFrames = 2, maxFrames = 6)
        jb.push(0, frame(0f))
        jb.push(1, frame(1f))
        jb.pull()
        for (s in 2L..40L) jb.push(s, frame(s.toFloat()))
        assertTrue(jb.queued <= 6, "queued ${jb.queued}")
        assertTrue(jb.pull()!![0] > 30f, "playback skipped ahead to recent audio")
    }
}
