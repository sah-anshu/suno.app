// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.patient

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Helper PIN handling. Only a salted SHA-256 hash is stored ("salt:hash", hex). The PIN keeps a
 * patient from changing settings by accident; it is not meant to resist a determined attacker
 * with access to the phone's storage.
 */
object PinCode {
    const val LENGTH = 4

    fun isValid(pin: String): Boolean = pin.length == LENGTH && pin.all { it in '0'..'9' }

    fun hash(
        pin: String,
        salt: ByteArray = newSalt(),
    ): String {
        require(isValid(pin)) { "PIN must be $LENGTH digits" }
        return salt.toHex() + ":" + digest(salt, pin).toHex()
    }

    fun matches(
        pin: String,
        stored: String?,
    ): Boolean {
        val parts = stored?.split(':')?.takeIf { it.size == 2 && isValid(pin) } ?: return false
        val salt = parts[0].fromHex()
        val expected = parts[1].fromHex()
        return salt != null && expected != null && MessageDigest.isEqual(digest(salt, pin), expected)
    }

    private fun digest(
        salt: ByteArray,
        pin: String,
    ): ByteArray = MessageDigest.getInstance("SHA-256").digest(salt + pin.toByteArray(Charsets.UTF_8))

    private fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray? =
        if (length % 2 != 0 || any { it !in '0'..'9' && it !in 'a'..'f' }) {
            null
        } else {
            chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }

    private const val SALT_BYTES = 16
}
