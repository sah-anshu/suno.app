// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.audiograph

/** Adds other sources to a captured microphone block before it is processed. */
fun interface InputMixer {
    /** Mixes into the first [frames] samples of [input] (16-bit mono at [sampleRateHz]) in place. */
    fun mix(
        input: ShortArray,
        frames: Int,
        sampleRateHz: Int,
    )
}

/** What the patient hears. AUTO lets a person talking come to the front. */
enum class SourceMode {
    AUTO,
    ROOM_ONLY,
    PERSON_ONLY,
    ;

    companion object {
        fun fromName(name: String?): SourceMode = entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/**
 * Mixes companions' voices into the patient's microphone block. While someone talks (AUTO), the
 * room microphone is turned down by about 12 dB with a short ramp, so the voice comes to the front
 * without the room disappearing. The result saturates instead of wrapping around.
 *
 * Runs before the listening chain, so the patient's audiogram and limiter apply to every source.
 */
class VoiceMixer(
    private val hub: RemoteAudioHub,
) : InputMixer {
    @Volatile
    var mode: SourceMode = SourceMode.AUTO

    private var micGain = 1f
    private var voice = FloatArray(0)

    /** Current room microphone gain (for tests and metering). */
    val roomGain: Float get() = micGain

    override fun mix(
        input: ShortArray,
        frames: Int,
        sampleRateHz: Int,
    ) {
        val n = minOf(frames, input.size)
        if (voice.size < n) voice = FloatArray(n)
        voice.fill(0f, 0, n)
        val current = mode
        val talking = current != SourceMode.ROOM_ONLY && hub.read(voice, n, sampleRateHz)
        val target =
            when {
                current == SourceMode.PERSON_ONLY -> 0f
                talking -> DUCKED_ROOM_GAIN
                else -> 1f
            }
        val step = 1f / (sampleRateHz * RAMP_MS / MS_PER_SECOND).coerceAtLeast(1)
        for (i in 0 until n) {
            micGain =
                when {
                    micGain < target -> (micGain + step).coerceAtMost(target)
                    micGain > target -> (micGain - step).coerceAtLeast(target)
                    else -> micGain
                }
            val mixed = input[i] * micGain + voice[i] * FULL_SCALE
            input[i] = mixed.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
        }
    }

    private companion object {
        /** About −12 dB. */
        const val DUCKED_ROOM_GAIN = 0.25f
        const val RAMP_MS = 80
        const val MS_PER_SECOND = 1000
        const val FULL_SCALE = 32767f
    }
}
