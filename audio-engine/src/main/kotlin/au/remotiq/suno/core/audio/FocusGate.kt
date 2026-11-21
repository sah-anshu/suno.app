// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.core.audio

/**
 * Fades processed output to silence while audio focus is paused and back in when it
 * returns, so a pause never clicks and a resume never starts at full level.
 *
 * Runs after the limiter, and only ever reduces level, so it cannot break the output ceiling.
 */
class FocusGate(
    rampSamples: Int,
) {
    private val step = 1f / rampSamples.coerceAtLeast(1)
    private var gain = 1f

    /** Current gain in [0, 1]. */
    val level: Float get() = gain

    fun apply(
        output: ShortArray,
        paused: Boolean,
    ) {
        val target = if (paused) 0f else 1f
        if (gain == target && target == 1f) return
        for (i in output.indices) {
            gain =
                when {
                    gain < target -> (gain + step).coerceAtMost(target)
                    gain > target -> (gain - step).coerceAtLeast(target)
                    else -> gain
                }
            output[i] = (output[i] * gain).toInt().toShort()
        }
    }
}
