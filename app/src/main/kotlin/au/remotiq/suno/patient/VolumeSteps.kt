// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.patient

import au.remotiq.suno.common.AudioLimits
import au.remotiq.suno.data.PatientSettings

/**
 * Maps the patient's six volume steps to master gain. Steps are evenly spaced in dB and always
 * inside [AudioLimits]; the per-ear limiter stays the last stage regardless.
 */
object VolumeSteps {
    private val STEP_DB = listOf(-12.0, -6.0, 0.0, 6.0, 12.0, 18.0)

    init {
        check(STEP_DB.size == PatientSettings.VOLUME_STEPS)
    }

    /** Effective step after the helper's cap and the place's cap. */
    fun effectiveStep(
        settings: PatientSettings,
        place: Place,
    ): Int {
        val cap = minOf(settings.maxVolumeStep, place.maxVolumeStep ?: PatientSettings.VOLUME_STEPS)
        return settings.volumeStep.coerceIn(1, cap.coerceAtLeast(1))
    }

    /** Master gain in dB for [step] (1-based), clamped to [AudioLimits]. */
    fun gainDb(step: Int): Double =
        STEP_DB[step.coerceIn(1, STEP_DB.size) - 1]
            .coerceIn(AudioLimits.MIN_MASTER_GAIN_DB, AudioLimits.MAX_MASTER_GAIN_CAP_DB)
}
