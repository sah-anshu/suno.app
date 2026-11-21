// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.audiograph

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/** A person whose voice is currently reaching the patient. */
data class Talker(
    val id: String,
    val name: String,
)

/**
 * Collects voice streams from approved companions and hands them to the audio thread at the
 * session's sample rate. Network threads call [push]; the audio thread calls [read] once per block.
 *
 * Streams arrive clean (no hearing profile applied). They are mixed into the capture block before
 * the patient's listening chain, so the patient's own audiogram and limiter shape them.
 */
class RemoteAudioHub(
    private val frameSamples: Int = FRAME_SAMPLES,
    private val streamRateHz: Int = STREAM_RATE_HZ,
) {
    private class Stream(
        val id: String,
        val name: String,
        frameSamples: Int,
    ) {
        val jitter = JitterBuffer(frameSamples)
        val fifo = FloatFifo()
        var resampler: Resampler? = null
        var outputRate = 0
    }

    private val streams = ConcurrentHashMap<String, Stream>()
    private val _talkers = MutableStateFlow<List<Talker>>(emptyList())

    /** People talking right now, in the order they started. */
    val talkers: StateFlow<List<Talker>> = _talkers.asStateFlow()

    /** Queue one decoded 20 ms frame (floats in [-1, 1]) from companion [id]. */
    fun push(
        id: String,
        name: String,
        sequence: Long,
        samples: FloatArray,
    ) {
        streams.getOrPut(id) { Stream(id, name, frameSamples) }.jitter.push(sequence, samples)
    }

    /** Forget a companion that left or was removed. */
    fun remove(id: String) {
        streams.remove(id)
        publish(_talkers.value.filterNot { it.id == id })
    }

    /**
     * Adds every active voice into the first [frames] samples of [out] at [sampleRateHz].
     * Returns true when at least one person contributed sound to this block.
     */
    fun read(
        out: FloatArray,
        frames: Int,
        sampleRateHz: Int,
    ): Boolean {
        var anyone = false
        var talking: MutableList<Talker>? = null
        for (stream in streams.values) {
            if (fill(stream, frames, sampleRateHz)) {
                stream.fifo.addInto(out, frames)
                anyone = true
                (talking ?: mutableListOf<Talker>().also { talking = it }) += Talker(stream.id, stream.name)
            }
        }
        val now = talking.orEmpty()
        if (now.map { it.id } != _talkers.value.map { it.id }) publish(now)
        return anyone
    }

    private fun fill(
        stream: Stream,
        frames: Int,
        sampleRateHz: Int,
    ): Boolean {
        if (stream.outputRate != sampleRateHz) {
            stream.resampler = Resampler(streamRateHz, sampleRateHz)
            stream.outputRate = sampleRateHz
            stream.fifo.clear()
        }
        val resampler = stream.resampler ?: return false
        while (stream.fifo.size < frames) {
            val frame = stream.jitter.pull() ?: break
            stream.fifo.addAll(resampler.process(frame))
        }
        return stream.fifo.size > 0
    }

    private fun publish(list: List<Talker>) {
        _talkers.value = list
    }

    companion object {
        const val STREAM_RATE_HZ = 16_000
        const val FRAME_MS = 20
        const val FRAME_SAMPLES = STREAM_RATE_HZ * FRAME_MS / 1000
    }
}

/** Minimal growable float queue used on the audio thread. */
internal class FloatFifo {
    private var data = FloatArray(INITIAL)
    private var start = 0
    var size = 0
        private set

    fun addAll(samples: FloatArray) {
        if (start + size + samples.size > data.size) {
            val grown = FloatArray(maxOf(data.size * 2, size + samples.size))
            data.copyInto(grown, 0, start, start + size)
            data = grown
            start = 0
        }
        samples.copyInto(data, start + size)
        size += samples.size
    }

    /** Adds up to [count] queued samples into [out] and removes them from the queue. */
    fun addInto(
        out: FloatArray,
        count: Int,
    ) {
        val n = minOf(count, size)
        for (i in 0 until n) out[i] += data[start + i]
        start += n
        size -= n
        if (size == 0) start = 0
    }

    fun clear() {
        start = 0
        size = 0
    }

    private companion object {
        const val INITIAL = 2048
    }
}
