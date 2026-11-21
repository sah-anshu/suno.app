package au.remotiq.suno.core.audio.dsp

import kotlin.math.sqrt

/**
 * Detects acoustic feedback ("howl") and ducks gain before it runs away.
 *
 * Feedback is a self-sustaining near-pure tone, so it is highly *periodic*: its
 * normalized autocorrelation at the oscillation lag is close to 1, far higher than
 * speech or noise. When a strong, sustained periodic component appears, the guard
 * progressively attenuates the signal, recovering once it clears.
 *
 * This is a pragmatic v1 guard (detect + duck), not a full adaptive feedback
 * canceller. It is pure and block-based, so its behaviour is unit-testable
 * (sine wave -> engages; noise/speech-like -> stays mostly open).
 */
class FeedbackSuppressor(
    sampleRateHz: Int,
    private val tonalityThreshold: Double = 0.9,
    private val activationRms: Double = 0.1,
    private val minGain: Float = 0.2f,
    private val attackPerBlock: Float = 0.5f,
    private val releasePerBlock: Float = 0.1f,
) {
    // Feedback in roughly the 500 Hz–4 kHz range -> these autocorrelation lags.
    private val minLag = (sampleRateHz / MAX_FEEDBACK_HZ).coerceAtLeast(1)
    private val maxLag = (sampleRateHz / MIN_FEEDBACK_HZ).coerceAtLeast(minLag + 1)

    private var currentGain = 1.0f

    /** Current attenuation applied, as a linear gain in (0, 1]. 1.0 = fully open. */
    val gain: Float get() = currentGain

    /** Inspect [buffer] for feedback and duck gain in place if detected. */
    fun process(buffer: FloatArray) {
        if (buffer.size <= maxLag) return

        val howling = detectsFeedback(buffer)
        val target = if (howling) minGain else 1.0f
        val rate = if (howling) attackPerBlock else releasePerBlock
        val startGain = currentGain
        val endGain = startGain + (target - startGain) * rate

        // Ramp the gain across the block so the ducking itself never clicks.
        val step = (endGain - startGain) / buffer.size
        for (i in buffer.indices) {
            buffer[i] = (buffer[i] * (startGain + step * i))
        }
        currentGain = endGain
    }

    fun reset() {
        currentGain = 1.0f
    }

    private fun detectsFeedback(buffer: FloatArray): Boolean {
        var energy = 0.0
        for (sample in buffer) energy += sample.toDouble() * sample
        // Quiet blocks cannot trigger the guard. Avoid their much costlier lag scan.
        return sqrt(energy / buffer.size) >= activationRms && tonality(buffer, energy) >= tonalityThreshold
    }

    /**
     * Strength of the periodic component whose period falls in the feedback lag range, in [0, 1].
     *
     * Each lag is normalized by the energy of the two overlapping segments only. Dividing by
     * the whole block's energy instead caps a pure tone at about 1 - lag / blockSize, which at
     * the live 192-frame block hid howls below roughly 2.5 kHz.
     *
     * Like a pitch detector, a lag only counts after the autocorrelation has first dipped below
     * zero. Bass-heavy sound (a voice's fundamental, rumble) correlates strongly at short lags
     * without being periodic there, and never produces that dip and rise within the range.
     *
     * Scans only until a lag establishes feedback; the exact peak is not needed.
     */
    private fun tonality(
        buffer: FloatArray,
        energy: Double,
    ): Double {
        if (energy <= 1e-12) return 0.0
        val n = buffer.size
        // Energy of the first `lag` and last `lag` samples, which fall outside the overlap.
        var head = buffer[0].toDouble() * buffer[0]
        var tail = buffer[n - 1].toDouble() * buffer[n - 1]
        var dipped = false
        var best = 0.0
        for (lag in 1..maxLag) {
            var corr = 0.0
            for (i in lag until n) {
                corr += buffer[i].toDouble() * buffer[i - lag]
            }
            val overlap = (energy - head) * (energy - tail)
            if (overlap > 1e-12) {
                val normalized = corr / sqrt(overlap)
                if (normalized < 0.0) dipped = true
                if (dipped && lag >= minLag && normalized > best) {
                    best = normalized
                    if (best >= tonalityThreshold) return best
                }
            }
            head += buffer[lag].toDouble() * buffer[lag]
            tail += buffer[n - 1 - lag].toDouble() * buffer[n - 1 - lag]
        }
        return best
    }

    private companion object {
        const val MIN_FEEDBACK_HZ = 500
        const val MAX_FEEDBACK_HZ = 4000
    }
}
