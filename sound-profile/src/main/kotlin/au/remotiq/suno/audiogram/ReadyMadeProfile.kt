// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.audiogram

import au.remotiq.suno.common.AudioEar
import au.remotiq.suno.common.FrequencyHz
import au.remotiq.suno.common.HearingDb

/**
 * Ready-made hearing profiles, so a person can start listening before anyone has run a
 * hearing check. The shapes are typical age-related (sloping) losses. They are estimates
 * for comfort, not a diagnosis, and a measured audiogram should replace them when possible.
 *
 * [levelsDb] are thresholds in dB HL at [FREQUENCIES], the same for both ears.
 */
enum class ReadyMadeProfile(
    val id: String,
    val displayName: String,
    private val levelsDb: List<Double>,
    /** Master gain cap in dB applied on top of the fitted curve; see AudioLimits. */
    val masterGainCapDb: Double,
) {
    /** Mild sloping loss (four-frequency average about 33 dB HL). The default. */
    MILD("ready-mild", "Mild (ready-made)", listOf(15.0, 20.0, 25.0, 35.0, 50.0, 60.0), 6.0),

    /** Moderate sloping loss (four-frequency average about 50 dB HL). */
    MODERATE("ready-moderate", "Moderate (ready-made)", listOf(30.0, 35.0, 45.0, 55.0, 65.0, 70.0), 6.0),

    /** Same shape as [MILD] with no extra master gain, for children and sensitive listeners. */
    CHILD_SAFE("ready-child-safe", "Child-safe (ready-made)", listOf(15.0, 20.0, 25.0, 35.0, 50.0, 60.0), 0.0),
    ;

    /** The profile as a per-ear audiogram. */
    val audiogram: HearingCurve
        get() =
            HearingCurve(
                AudioEar.entries.flatMap { ear ->
                    FREQUENCIES.zip(levelsDb) { frequency, level -> HearingPoint(ear, frequency, HearingDb(level)) }
                },
            )

    companion object {
        val FREQUENCIES: List<FrequencyHz> = listOf(250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0).map(::FrequencyHz)

        /** Used when no profile has been saved or selected yet. */
        val DEFAULT: ReadyMadeProfile = MILD

        fun fromId(id: String?): ReadyMadeProfile? = entries.firstOrNull { it.id == id }
    }
}
