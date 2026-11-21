// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.audiograph

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal fun sine(
    hz: Double,
    rate: Int,
    count: Int,
    amplitude: Double = 0.5,
    offset: Int = 0,
) = FloatArray(count) { (amplitude * sin(2 * PI * hz * (it + offset) / rate)).toFloat() }

internal fun rms(x: FloatArray): Double = sqrt(x.sumOf { it.toDouble() * it } / x.size)

/** Magnitude of [hz] in [x] relative to a full-scale sine (Goertzel). */
internal fun level(
    x: FloatArray,
    hz: Double,
    rate: Int,
): Double {
    val w = 2 * PI * hz / rate
    val coeff = 2 * cos(w)
    var s1 = 0.0
    var s2 = 0.0
    for (v in x) {
        val s = v + coeff * s1 - s2
        s2 = s1
        s1 = s
    }
    val power = s1 * s1 + s2 * s2 - coeff * s1 * s2
    return 2 * sqrt(power.coerceAtLeast(0.0)) / x.size
}
