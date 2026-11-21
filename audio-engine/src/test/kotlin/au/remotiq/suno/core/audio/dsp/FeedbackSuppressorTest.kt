package au.remotiq.suno.core.audio.dsp

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class FeedbackSuppressorTest {
    private val sampleRate = 48_000
    private val blockSize = 1024

    private fun process(
        guard: FeedbackSuppressor,
        signal: (Int) -> Float,
        blocks: Int,
    ): Float {
        var sample = 0
        repeat(blocks) {
            val block = FloatArray(blockSize) { signal(sample++) }
            guard.process(block)
        }
        return guard.gain
    }

    @Test
    fun `engages on a sustained loud tone (feedback howl)`() {
        val guard = FeedbackSuppressor(sampleRate)
        val gain = process(guard, { i -> (0.6 * sin(2.0 * PI * 2000.0 * i / sampleRate)).toFloat() }, blocks = 30)
        assertTrue(gain < 0.5f, "guard should duck strongly on a sustained tone, gain=$gain")
    }

    @Test
    fun `stays mostly open on broadband noise (not feedback)`() {
        val guard = FeedbackSuppressor(sampleRate)
        val rng = Random(42)
        val gain = process(guard, { (rng.nextDouble(-0.6, 0.6)).toFloat() }, blocks = 30)
        assertTrue(gain > 0.8f, "guard should not duck noise much, gain=$gain")
    }

    @Test
    fun `recovers after the tone stops`() {
        val guard = FeedbackSuppressor(sampleRate)
        process(guard, { i -> (0.6 * sin(2.0 * PI * 2000.0 * i / sampleRate)).toFloat() }, blocks = 30)
        val recovered = process(guard, { 0f }, blocks = 60)
        assertTrue(recovered > 0.8f, "guard should reopen once feedback clears, gain=$recovered")
    }

    private fun processBlocks(
        guard: FeedbackSuppressor,
        size: Int,
        signal: (Int) -> Float,
        blocks: Int,
    ): Float {
        var sample = 0
        repeat(blocks) {
            val block = FloatArray(size) { signal(sample++) }
            guard.process(block)
        }
        return guard.gain
    }

    @Test
    fun `engages at the live 192-frame block for common howl pitches`() {
        listOf(600.0, 1_500.0, 2_000.0, 3_000.0).forEach { hz ->
            val guard = FeedbackSuppressor(sampleRate)
            val howl = { i: Int -> (0.6 * sin(2.0 * PI * hz * i / sampleRate)).toFloat() }
            val gain = processBlocks(guard, LIVE_BLOCK, howl, 60)
            assertTrue(gain < 0.5f, "guard should duck a $hz Hz howl at 192-frame blocks, gain=$gain")
        }
    }

    @Test
    fun `stays mostly open on noise at the live block size`() {
        val guard = FeedbackSuppressor(sampleRate)
        val rng = Random(7)
        val gain = processBlocks(guard, LIVE_BLOCK, { rng.nextDouble(-0.6, 0.6).toFloat() }, 120)
        assertTrue(gain > 0.8f, "guard should not duck noise at 192-frame blocks, gain=$gain")
    }

    @Test
    fun `stays mostly open on a voiced speech-like sound at the live block size`() {
        val guard = FeedbackSuppressor(sampleRate)
        val f0 = 140.0
        val voice = { i: Int ->
            var v = 0.0
            for (k in 1..25) v += sin(2.0 * PI * f0 * k * i / sampleRate) / k
            (0.25 * v).toFloat()
        }
        val gain = processBlocks(guard, LIVE_BLOCK, voice, 120)
        assertTrue(gain > 0.8f, "guard should not duck a voiced vowel, gain=$gain")
    }

    @Test
    fun `stays mostly open on low rumble at the live block size`() {
        val guard = FeedbackSuppressor(sampleRate)
        val rumble = { i: Int -> (0.6 * sin(2.0 * PI * 80.0 * i / sampleRate)).toFloat() }
        val gain = processBlocks(guard, LIVE_BLOCK, rumble, 120)
        assertTrue(gain > 0.8f, "an 80 Hz rumble is not feedback, gain=$gain")
    }

    private companion object {
        const val LIVE_BLOCK = 192
    }
}
