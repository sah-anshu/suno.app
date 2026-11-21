// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.patient

import au.remotiq.suno.core.audio.dsp.ListeningPreset
import au.remotiq.suno.core.audio.speech.BassReduction
import au.remotiq.suno.core.audio.speech.EnhancementEngine
import au.remotiq.suno.core.audio.speech.EnhancementOptions
import au.remotiq.suno.core.audio.speech.NoiseStrength
import au.remotiq.suno.core.audio.speech.SpeechPresence

/**
 * Where the patient is. Each place is a ready-made combination of EarCast's existing processing
 * options, so the patient picks a place instead of tuning engines. The audiogram still applies
 * in every place; places only change noise handling and emphasis.
 */
enum class Place(
    val preset: ListeningPreset,
    private val noise: NoiseStrength,
    private val bass: BassReduction,
    private val clarity: SpeechPresence,
    /** Highest volume step allowed in this place, or null for the helper's cap. */
    val maxVolumeStep: Int? = null,
) {
    /** Indoors: TV and talking. */
    HOME(ListeningPreset.STANDARD, NoiseStrength.GENTLE, BassReduction.GENTLE, SpeechPresence.GENTLE),

    /** Open air: stronger low cut against wind. */
    PARK(ListeningPreset.CONVERSATION, NoiseStrength.GENTLE, BassReduction.STRONG, SpeechPresence.GENTLE),

    /** Road noise: strongest noise reduction and low cut, more speech emphasis. */
    TRAFFIC(ListeningPreset.CONVERSATION, NoiseStrength.STRONG, BassReduction.STRONG, SpeechPresence.STRONG),

    /** Quiet room at night: softer, no loud steps. */
    NIGHT(
        ListeningPreset.STANDARD,
        NoiseStrength.GENTLE,
        BassReduction.GENTLE,
        SpeechPresence.GENTLE,
        maxVolumeStep = 3,
    ),
    ;

    /**
     * Applies this place to [base], keeping the helper's engine choice. A low-delay engine is
     * used when the helper has not picked one, because Room listening is sensitive to delay.
     */
    fun applyTo(base: EnhancementOptions): EnhancementOptions =
        base.copy(
            noiseReduction = noise,
            voiceComfort = bass,
            speechClarity = clarity,
            speechEngine = lowDelayUnlessChosen(base),
        )

    companion object {
        private fun lowDelayUnlessChosen(base: EnhancementOptions): EnhancementEngine =
            if (base.noiseReduction == NoiseStrength.OFF) EnhancementEngine.RNNOISE else base.speechEngine

        fun fromName(name: String?): Place = entries.firstOrNull { it.name == name } ?: HOME
    }
}
