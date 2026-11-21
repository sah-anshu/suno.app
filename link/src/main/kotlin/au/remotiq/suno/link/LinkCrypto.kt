// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.link

import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Which way, and on which channel, a sealed frame travels. Each has its own nonce space. */
enum class Lane(
    val code: Byte,
) {
    CLIENT_CONTROL(1),
    HOST_CONTROL(2),
    CLIENT_AUDIO(3),
}

/**
 * Per-connection encryption. Every connection starts with a fresh random salt from the companion,
 * so each connection has its own AES-256-GCM key, derived with HKDF-SHA256 from the join secret.
 * Bluetooth's insecure L2CAP channel has no link encryption, so this protects every frame on every
 * transport the same way.
 */
class LinkCrypto(
    secret: ByteArray,
    salt: ByteArray,
) {
    private val key = SecretKeySpec(hkdf(secret, salt, INFO, KEY_BYTES), "AES")
    private val sendCounters = LongArray(Lane.entries.size)
    private val windows = Lane.entries.associateWith { ReplayWindow() }

    /** Encrypts [plain] for [lane]: `[counter: 8][ciphertext + tag]`. */
    fun seal(
        lane: Lane,
        plain: ByteArray,
    ): ByteArray {
        val counter = synchronized(sendCounters) { ++sendCounters[lane.ordinal] }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(lane, counter)))
        val body = cipher.doFinal(plain)
        return ByteBuffer
            .allocate(Long.SIZE_BYTES + body.size)
            .putLong(counter)
            .put(body)
            .array()
    }

    /** Decrypts a frame from [lane]; returns null if it is forged, damaged or replayed. */
    fun open(
        lane: Lane,
        sealed: ByteArray,
    ): ByteArray? {
        if (sealed.size < Long.SIZE_BYTES + TAG_BITS / Byte.SIZE_BITS) return null
        val buffer = ByteBuffer.wrap(sealed)
        val counter = buffer.long
        val window = windows.getValue(lane)
        if (!window.mayAccept(counter)) return null
        val plain =
            runCatching {
                val cipher = Cipher.getInstance(TRANSFORM)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(lane, counter)))
                cipher.doFinal(sealed, Long.SIZE_BYTES, sealed.size - Long.SIZE_BYTES)
            }.getOrNull() ?: return null
        return if (window.accept(counter)) plain else null
    }

    private fun nonce(
        lane: Lane,
        counter: Long,
    ): ByteArray =
        ByteBuffer
            .allocate(NONCE_BYTES)
            .put(lane.code)
            .put(ByteArray(3))
            .putLong(counter)
            .array()

    /** Accepts each counter once, within the most recent [SIZE] counters (audio may arrive out of order). */
    private class ReplayWindow {
        private var highest = 0L
        private var seen = 0L

        @Synchronized
        fun mayAccept(counter: Long): Boolean = counter > 0 && counter > highest - SIZE

        @Synchronized
        fun accept(counter: Long): Boolean {
            if (counter > highest) {
                val shift = counter - highest
                seen = if (shift >= SIZE) 0L else seen shl shift.toInt()
                seen = seen or 1L
                highest = counter
                return true
            }
            val bit = 1L shl (highest - counter).toInt()
            if (highest - counter >= SIZE || seen and bit != 0L) return false
            seen = seen or bit
            return true
        }

        companion object {
            const val SIZE = 64
        }
    }

    companion object {
        const val SALT_BYTES = 16
        private const val KEY_BYTES = 32
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private val INFO = "suno-link-v1".toByteArray()

        /** HKDF-SHA256 (RFC 5869). */
        fun hkdf(
            ikm: ByteArray,
            salt: ByteArray,
            info: ByteArray,
            length: Int,
        ): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(salt, "HmacSHA256"))
            val prk = mac.doFinal(ikm)
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            val out = ByteArray(length)
            var previous = ByteArray(0)
            var offset = 0
            var block = 1
            while (offset < length) {
                mac.update(previous)
                mac.update(info)
                mac.update(block.toByte())
                previous = mac.doFinal()
                val n = minOf(previous.size, length - offset)
                previous.copyInto(out, offset, 0, n)
                offset += n
                block++
            }
            return out
        }
    }
}
