// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.audiograph

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * Streaming sample-rate converter using a windowed-sinc (Blackman) interpolator.
 *
 * Feed input blocks of any size to [process]; it returns every output sample that can be computed
 * so far. The low-pass cut-off sits just below the lower of the two Nyquist frequencies, so
 * up-sampling 16 kHz speech to 48 kHz adds no images and down-sampling does not alias.
 */
class Resampler(
    private val inputRateHz: Int,
    private val outputRateHz: Int,
    private val halfTaps: Int = DEFAULT_HALF_TAPS,
) {
    init {
        require(inputRateHz > 0 && outputRateHz > 0) { "Sample rates must be positive" }
        require(halfTaps >= 2) { "Need at least 2 taps per side" }
    }

    /** Input samples consumed per output sample. */
    private val step = inputRateHz.toDouble() / outputRateHz

    /** Cut-off as a fraction of the input sample rate. */
    private val cutoff = CUTOFF_FACTOR * 0.5 * min(1.0, outputRateHz.toDouble() / inputRateHz)

    /** Pending input, starting with [halfTaps] samples of context before the read position. */
    private var buffer = FloatArray(halfTaps)
    private var size = halfTaps

    /** Read position in [buffer], in input samples. */
    private var position = halfTaps.toDouble()

    fun process(input: FloatArray): FloatArray {
        append(input)
        val out = ArrayList<Float>((input.size / step).toInt() + 2)
        while (position + halfTaps < size) {
            out += interpolate(position)
            position += step
        }
        compact()
        return out.toFloatArray()
    }

    fun reset() {
        buffer = FloatArray(halfTaps)
        size = halfTaps
        position = halfTaps.toDouble()
    }

    private fun interpolate(at: Double): Float {
        val base = floor(at).toInt()
        val frac = at - base
        var sum = 0.0
        var weights = 0.0
        for (k in -halfTaps + 1..halfTaps) {
            val index = base + k
            val distance = k - frac
            val w = kernel(distance)
            sum += buffer[index] * w
            weights += w
        }
        // Normalising by the kernel sum keeps unity gain at DC for any fractional position.
        return (if (abs(weights) > 1e-9) sum / weights else 0.0).toFloat()
    }

    private fun kernel(distance: Double): Double {
        val x = distance / halfTaps
        if (abs(x) >= 1.0) return 0.0
        val window = BLACKMAN_A0 + BLACKMAN_A1 * cos(PI * x) + BLACKMAN_A2 * cos(2 * PI * x)
        val arg = 2 * PI * cutoff * distance
        val sinc = if (abs(arg) < 1e-9) 1.0 else sin(arg) / arg
        return sinc * window
    }

    private fun append(input: FloatArray) {
        if (size + input.size > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + input.size))
        input.copyInto(buffer, size)
        size += input.size
    }

    /** Drops input that is no longer needed as context. */
    private fun compact() {
        val keepFrom = (floor(position).toInt() - halfTaps).coerceAtLeast(0)
        if (keepFrom == 0) return
        buffer.copyInto(buffer, 0, keepFrom, size)
        size -= keepFrom
        position -= keepFrom
    }

    private companion object {
        const val DEFAULT_HALF_TAPS = 16
        const val CUTOFF_FACTOR = 0.92
        const val BLACKMAN_A0 = 0.42
        const val BLACKMAN_A1 = 0.5
        const val BLACKMAN_A2 = 0.08
    }
}
