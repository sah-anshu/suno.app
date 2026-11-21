// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.audiograph

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/** What a remote stream carries. Voices take priority over media such as TV sound. */
enum class StreamKind { VOICE, MEDIA }

/** A remote source currently reaching the patient: a person's voice, or shared sound such as a TV. */
data class Talker(
    val id: String,
    val name: String,
    val kind: StreamKind = StreamKind.VOICE,
)

/** Which kinds of remote sound were added to a block. */
data class RemoteActivity(
    val voice: Boolean,
    val media: Boolean,
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
        val kind: StreamKind,
        frameSamples: Int,
    ) {
        val jitter = JitterBuffer(frameSamples)
        val fifo = FloatFifo()
        var resampler: Resampler? = null
        var outputRate = 0
    }

    private val streams = ConcurrentHashMap<String, Stream>()
    private val _active = MutableStateFlow<List<Talker>>(emptyList())

    /** Every remote source sounding right now (voices and media), in the order they started. */
    val active: StateFlow<List<Talker>> = _active.asStateFlow()

    /** People talking right now. */
    val talkers: List<Talker> get() = _active.value.filter { it.kind == StreamKind.VOICE }

    /** Queue one decoded 20 ms frame (floats in [-1, 1]) of [kind] from companion [id]. */
    fun push(
        id: String,
        name: String,
        sequence: Long,
        samples: FloatArray,
        kind: StreamKind = StreamKind.VOICE,
    ) {
        streams.getOrPut(key(id, kind)) { Stream(id, name, kind, frameSamples) }.jitter.push(sequence, samples)
    }

    /** Forget a companion that left or was removed, with all its streams. */
    fun remove(id: String) {
        StreamKind.entries.forEach { streams.remove(key(id, it)) }
        publish(_active.value.filterNot { it.id == id })
    }

    /**
     * Adds every active source into the first [frames] samples of [voiceOut] (people) or
     * [mediaOut] (shared sound such as TV) at [sampleRateHz], so the mixer can give voices priority.
     */
    fun read(
        voiceOut: FloatArray,
        mediaOut: FloatArray,
        frames: Int,
        sampleRateHz: Int,
    ): RemoteActivity {
        var voice = false
        var media = false
        var now: MutableList<Talker>? = null
        for (stream in streams.values) {
            if (!fill(stream, frames, sampleRateHz)) continue
            if (stream.kind == StreamKind.VOICE) {
                stream.fifo.addInto(voiceOut, frames)
                voice = true
            } else {
                stream.fifo.addInto(mediaOut, frames)
                media = true
            }
            (now ?: mutableListOf<Talker>().also { now = it }) += Talker(stream.id, stream.name, stream.kind)
        }
        val list = now.orEmpty()
        if (list.map { it.id to it.kind } != _active.value.map { it.id to it.kind }) publish(list)
        return RemoteActivity(voice, media)
    }

    /** Voices and media summed into one buffer (for callers that do not need priorities). */
    fun read(
        out: FloatArray,
        frames: Int,
        sampleRateHz: Int,
    ): Boolean {
        val activity = read(out, out, frames, sampleRateHz)
        return activity.voice || activity.media
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
        _active.value = list
    }

    private fun key(
        id: String,
        kind: StreamKind,
    ) = "$id#${kind.name}"

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
