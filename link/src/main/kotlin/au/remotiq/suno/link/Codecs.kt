// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.link

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Encodes one 20 ms, 16 kHz mono frame for the link. */
interface FrameCodec {
    val id: Int

    fun encode(pcm: ShortArray): ByteArray

    /** Decodes to floats in [-1, 1]; returns null for a frame it cannot decode. */
    fun decode(payload: ByteArray): FloatArray?
}

object CodecIds {
    /** Uncompressed 16-bit little-endian PCM (Wi-Fi Direct has plenty of bandwidth). */
    const val PCM16 = 1

    /** Opus, for Bluetooth LE's lower bandwidth (Android implementation in link-android). */
    const val OPUS = 2
}

class PcmCodec(
    private val frameSamples: Int,
) : FrameCodec {
    override val id = CodecIds.PCM16

    override fun encode(pcm: ShortArray): ByteArray {
        val buffer = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { buffer.putShort(it) }
        return buffer.array()
    }

    override fun decode(payload: ByteArray): FloatArray? {
        if (payload.size != frameSamples * 2) return null
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(frameSamples) { buffer.short / PCM_SCALE }
    }

    private companion object {
        const val PCM_SCALE = 32768f
    }
}
