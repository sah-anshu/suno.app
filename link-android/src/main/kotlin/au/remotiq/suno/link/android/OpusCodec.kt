// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.link.android

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import androidx.annotation.RequiresApi
import au.remotiq.suno.link.CodecIds
import au.remotiq.suno.link.FrameCodec
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Opus through Android's built-in MediaCodec (encoder available from Android 10), used on
 * Bluetooth LE where bandwidth is limited. Frames are 20 ms of 16 kHz mono. Not covered by unit
 * tests (MediaCodec needs a device); see docs/DEVICE_TESTING.md.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class OpusCodec(
    private val frameSamples: Int,
    private val sampleRateHz: Int = 16_000,
    bitrate: Int = 24_000,
) : FrameCodec,
    AutoCloseable {
    override val id = CodecIds.OPUS

    private val encoder =
        MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS).apply {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, sampleRateHz, 1)
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, frameSamples * 2)
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }

    private val decoder =
        MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS).apply {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, sampleRateHz, 1)
            format.setByteBuffer("csd-0", ByteBuffer.wrap(opusHead()))
            format.setByteBuffer("csd-1", longLe(PRE_SKIP_NS))
            format.setByteBuffer("csd-2", longLe(SEEK_PREROLL_NS))
            configure(format, null, null, 0)
            start()
        }

    private val decimator = Decimator3()
    private var encodedFrames = 0L
    private val pendingPackets = ArrayDeque<ByteArray>()

    @Synchronized
    override fun encode(pcm: ShortArray): ByteArray {
        val index = encoder.dequeueInputBuffer(TIMEOUT_US)
        if (index >= 0) {
            val input = checkNotNull(encoder.getInputBuffer(index)).order(ByteOrder.LITTLE_ENDIAN)
            input.clear()
            pcm.forEach { input.putShort(it) }
            encoder.queueInputBuffer(index, 0, pcm.size * 2, encodedFrames++ * FRAME_US, 0)
        }
        drain(encoder) { bytes, flags ->
            if (flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG ==
                0
            ) {
                pendingPackets.addLast(bytes)
            }
        }
        // The encoder may need a few frames before its first packet; an empty payload decodes to nothing.
        return pendingPackets.removeFirstOrNull() ?: ByteArray(0)
    }

    @Synchronized
    override fun decode(payload: ByteArray): FloatArray? {
        if (payload.isEmpty()) return null
        val index = decoder.dequeueInputBuffer(TIMEOUT_US)
        if (index < 0) return null
        checkNotNull(decoder.getInputBuffer(index)).apply {
            clear()
            put(payload)
        }
        decoder.queueInputBuffer(index, 0, payload.size, 0, 0)
        val pcm = ArrayList<Short>(frameSamples * DECIMATION)
        drain(decoder) { bytes, _ ->
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            while (b.remaining() >= 2) pcm += b.short
        }
        if (pcm.isEmpty()) return null
        val outRate = decoder.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val floats = FloatArray(pcm.size) { pcm[it] / PCM_SCALE }
        val atStreamRate = if (outRate == sampleRateHz * DECIMATION) decimator.process(floats) else floats
        // Exactly one frame for the jitter buffer: pad or trim small codec-size differences.
        return FloatArray(frameSamples) { if (it < atStreamRate.size) atStreamRate[it] else 0f }
    }

    override fun close() {
        runCatching { encoder.stop() }
        runCatching { encoder.release() }
        runCatching { decoder.stop() }
        runCatching { decoder.release() }
    }

    private fun drain(
        codec: MediaCodec,
        onOutput: (ByteArray, Int) -> Unit,
    ) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) continue
            if (index < 0) return
            val buffer = checkNotNull(codec.getOutputBuffer(index))
            val bytes = ByteArray(info.size)
            buffer.position(info.offset)
            buffer.get(bytes)
            codec.releaseOutputBuffer(index, false)
            onOutput(bytes, info.flags)
        }
    }

    /** RFC 7845 identification header for one channel. */
    private fun opusHead(): ByteArray =
        ByteBuffer
            .allocate(OPUS_HEAD_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put("OpusHead".toByteArray())
            .put(1)
            .put(1)
            .putShort(PRE_SKIP_SAMPLES.toShort())
            .putInt(sampleRateHz)
            .putShort(0)
            .put(0)
            .array()

    private fun longLe(value: Long): ByteBuffer =
        ByteBuffer.allocate(Long.SIZE_BYTES).order(ByteOrder.nativeOrder()).putLong(value).apply {
            flip()
        }

    private companion object {
        const val TIMEOUT_US = 5_000L
        const val FRAME_US = 20_000L
        const val DECIMATION = 3
        const val PCM_SCALE = 32768f
        const val OPUS_HEAD_BYTES = 19
        const val PRE_SKIP_SAMPLES = 312
        const val PRE_SKIP_NS = 6_500_000L
        const val SEEK_PREROLL_NS = 80_000_000L
    }
}

/** Low-pass and keep every third sample: 48 kHz decoder output to the 16 kHz link rate. */
internal class Decimator3 {
    private val history = FloatArray(TAPS.size)
    private var phase = 0

    fun process(input: FloatArray): FloatArray {
        val out = ArrayList<Float>(input.size / 3 + 1)
        for (x in input) {
            history.copyInto(history, 1, 0, history.size - 1)
            history[0] = x
            if (phase == 0) {
                var acc = 0f
                for (i in TAPS.indices) acc += TAPS[i] * history[i]
                out += acc
            }
            phase = (phase + 1) % 3
        }
        return out.toFloatArray()
    }

    private companion object {
        /** 31-tap windowed-sinc low-pass at about 7 kHz for 48 kHz input, unity DC gain. */
        val TAPS: FloatArray =
            run {
                val n = 31
                val fc = 7_000.0 / 48_000.0
                val raw =
                    DoubleArray(n) { i ->
                        val m = i - (n - 1) / 2.0
                        val sinc = if (m == 0.0) 2 * fc else kotlin.math.sin(2 * Math.PI * fc * m) / (Math.PI * m)
                        val window = 0.54 - 0.46 * kotlin.math.cos(2 * Math.PI * i / (n - 1))
                        sinc * window
                    }
                val sum = raw.sum()
                FloatArray(n) { (raw[it] / sum).toFloat() }
            }
    }
}
