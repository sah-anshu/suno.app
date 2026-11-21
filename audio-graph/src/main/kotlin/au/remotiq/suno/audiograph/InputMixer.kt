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

/** What the patient hears. AUTO gives priority to a person talking, then TV sound, then the room. */
enum class SourceMode {
    AUTO,
    ROOM_ONLY,
    TV_ONLY,
    PERSON_ONLY,
    ;

    companion object {
        fun fromName(name: String?): SourceMode = entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/**
 * Mixes remote sources into the patient's microphone block with priorities: a person talking comes
 * first, then shared sound such as TV, then the room. Lower-priority sources are turned down by about
 * 12 dB with short ramps rather than cut, so nothing disappears abruptly. The result saturates
 * instead of wrapping around.
 *
 * Runs before the listening chain, so the patient's audiogram and limiter apply to every source.
 */
class VoiceMixer(
    private val hub: RemoteAudioHub,
) : InputMixer {
    @Volatile
    var mode: SourceMode = SourceMode.AUTO

    private val room = Ramp()
    private val media = Ramp()
    private val voiceGain = Ramp()
    private var voice = FloatArray(0)
    private var shared = FloatArray(0)

    /** Current room microphone gain (for tests and metering). */
    val roomGain: Float get() = room.value

    /** Current gain on shared media such as TV sound. */
    val mediaGain: Float get() = media.value

    override fun mix(
        input: ShortArray,
        frames: Int,
        sampleRateHz: Int,
    ) {
        val n = minOf(frames, input.size)
        if (voice.size < n) {
            voice = FloatArray(n)
            shared = FloatArray(n)
        }
        voice.fill(0f, 0, n)
        shared.fill(0f, 0, n)
        val activity = hub.read(voice, shared, n, sampleRateHz)
        val targets = targets(mode, activity)
        val step = 1f / (sampleRateHz * RAMP_MS / MS_PER_SECOND).coerceAtLeast(1)
        for (i in 0 until n) {
            val r = room.next(targets.room, step)
            val m = media.next(targets.media, step)
            val v = voiceGain.next(targets.voice, step)
            val mixed = input[i] * r + (shared[i] * m + voice[i] * v) * FULL_SCALE
            input[i] = mixed.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
        }
    }

    private data class Targets(
        val room: Float,
        val media: Float,
        val voice: Float,
    )

    private fun targets(
        mode: SourceMode,
        activity: RemoteActivity,
    ): Targets {
        val underVoice = if (activity.voice) DUCKED else 1f
        return when (mode) {
            SourceMode.AUTO ->
                Targets(
                    room =
                        if (activity.voice ||
                            activity.media
                        ) {
                            DUCKED
                        } else {
                            1f
                        },
                    media = underVoice,
                    voice = 1f,
                )
            SourceMode.ROOM_ONLY -> Targets(room = 1f, media = 0f, voice = 0f)
            SourceMode.TV_ONLY -> Targets(room = 0f, media = underVoice, voice = 1f)
            SourceMode.PERSON_ONLY -> Targets(room = 0f, media = 0f, voice = 1f)
        }
    }

    private class Ramp {
        var value = 1f
            private set

        fun next(
            target: Float,
            step: Float,
        ): Float {
            value =
                when {
                    value < target -> (value + step).coerceAtMost(target)
                    value > target -> (value - step).coerceAtLeast(target)
                    else -> value
                }
            return value
        }
    }

    private companion object {
        /** About −12 dB. */
        const val DUCKED = 0.25f
        const val RAMP_MS = 80
        const val MS_PER_SECOND = 1000
        const val FULL_SCALE = 32767f
    }
}
