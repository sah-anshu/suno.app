// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.core.audio.dsp

import au.remotiq.suno.audiogram.FrequencyGainCurve
import au.remotiq.suno.audiogram.FrequencyGainPoint
import au.remotiq.suno.audiograph.RemoteAudioHub
import au.remotiq.suno.audiograph.VoiceMixer
import au.remotiq.suno.common.FrequencyHz
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The audiogram rule (arch.md section 6.1): a companion's voice reaches the patient only through
 * the patient's own listening chain, so the profile shapes it and the limiter bounds it.
 */
class RemoteVoiceChainTest {
    private val rate = 48_000
    private val block = 192
    private val ceiling = 0.5f

    /** Same gain at the audiogram frequencies around the 1 kHz test voice. */
    private fun curve(gainDb: Double) =
        FrequencyGainCurve(listOf(500.0, 1000.0, 2000.0).map { FrequencyGainPoint(FrequencyHz(it), gainDb) })

    /** Runs a live-paced 1 kHz voice through mixer + chain and returns the right-ear output. */
    private fun render(
        profileDb: Double,
        voiceAmplitude: Double,
    ): FloatArray {
        val hub = RemoteAudioHub()
        val mixer = VoiceMixer(hub)
        val chain =
            StereoListeningChain(
                MonoListeningChain(curve(profileDb), rate, masterGainDb = 0.0, ceilingLinear = ceiling),
                MonoListeningChain(curve(profileDb), rate, masterGainDb = 0.0, ceilingLinear = ceiling),
                block,
            )
        val frame = RemoteAudioHub.FRAME_SAMPLES

        fun push(seq: Long) =
            hub.push(
                "c",
                "C",
                seq,
                FloatArray(frame) {
                    (
                        voiceAmplitude *
                            sin(2 * PI * 1000.0 * (seq * frame + it) / 16_000)
                    ).toFloat()
                },
            )
        (0L until 3L).forEach(::push)
        val out = mutableListOf<Float>()
        repeat(500) { i ->
            if (i % 5 == 0) push(3L + i / 5)
            val mic = ShortArray(block)
            mixer.mix(mic, block, rate)
            val stereo = FloatArray(block * 2) { mic[it / 2] / 32768f }
            chain.process(stereo)
            for (f in 0 until block) out += stereo[2 * f + 1]
        }
        return out.toFloatArray()
    }

    private fun rms(x: FloatArray) = sqrt(x.sumOf { it.toDouble() * it } / x.size)

    // Quiet enough to stay below the compressor threshold, so the profile gain shows directly.
    @Test
    fun `the patient's profile gain is applied to a companion's voice`() {
        val flat = rms(render(profileDb = 0.0, voiceAmplitude = 0.001).copyOfRange(rate / 2, rate * 2))
        val boosted = rms(render(profileDb = 20.0, voiceAmplitude = 0.001).copyOfRange(rate / 2, rate * 2))
        assertTrue(boosted > flat * 3, "profile should raise the voice: flat=$flat boosted=$boosted")
    }

    @Test
    fun `a loud companion voice never exceeds the limiter ceiling`() {
        val out = render(profileDb = 40.0, voiceAmplitude = 1.0)
        val peak = out.maxOf { abs(it) }
        assertTrue(peak <= ceiling + 1e-4f, "peak $peak above ceiling $ceiling")
    }
}
