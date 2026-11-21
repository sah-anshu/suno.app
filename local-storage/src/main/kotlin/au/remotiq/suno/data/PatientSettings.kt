// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.data

/**
 * Settings for the patient's simple layout. The helper changes them behind the PIN; the patient
 * changes only [place] and [volumeStep] from Home.
 *
 * Enum-like values are stored as names so the storage module does not depend on UI types.
 */
data class PatientSettings(
    /** Place preset name: HOME, PARK, TRAFFIC or NIGHT. */
    val place: String = "HOME",
    /** Volume step 1..[maxVolumeStep]. */
    val volumeStep: Int = 3,
    /** Highest step the patient may reach, set by the helper (1..[VOLUME_STEPS]). */
    val maxVolumeStep: Int = VOLUME_STEPS,
    /** Pictures mode: symbols with one word. Words mode adds a short explanation. */
    val picturesMode: Boolean = true,
    /** Colour theme name: STANDARD, DARK or YELLOW. */
    val theme: String = "STANDARD",
    /** 1 Simplest, 2 Standard, 3 Full. */
    val homeLevel: Int = 2,
    /** Start listening when earphones connect while Suno is open. */
    val autoStart: Boolean = false,
    /** Salted hash of the helper PIN, or null before a PIN is set. Never the PIN itself. */
    val pinHash: String? = null,
    /** Name shown to family on their phones and in the join code; empty means "Suno". */
    val displayName: String = "",
) {
    fun normalized(): PatientSettings {
        val cap = maxVolumeStep.coerceIn(1, VOLUME_STEPS)
        return copy(
            maxVolumeStep = cap,
            volumeStep = volumeStep.coerceIn(1, cap),
            homeLevel = homeLevel.coerceIn(1, HOME_LEVELS),
            displayName = displayName.trim().take(MAX_NAME),
        )
    }

    companion object {
        const val VOLUME_STEPS = 6
        const val HOME_LEVELS = 3
        const val MAX_NAME = 40
    }
}
