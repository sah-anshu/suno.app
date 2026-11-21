// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.link

import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64

/** Name and passphrase of the patient's private Wi-Fi Direct group. */
data class WifiDirectCredentials(
    val networkName: String,
    val passphrase: String,
) {
    init {
        require(networkName.startsWith("DIRECT-") && networkName.length in 9..32) { "Invalid Wi-Fi Direct name" }
        require(passphrase.length in 8..63) { "Invalid Wi-Fi Direct passphrase" }
    }
}

/**
 * What a companion needs to reach the patient's Suno, carried in the join QR code:
 * `suno://join?v=1&id=..&k=..&n=..&w=..&p=..&b=1`.
 *
 * The secret only lets a phone ask to join; the patient's phone must still approve it.
 */
class JoinCode(
    /** 16 hex characters; identifies the patient's Suno on BLE and in remembered joins. */
    val sessionId: String,
    /** 32 random bytes; keys for every connection are derived from it. */
    val secret: ByteArray,
    val patientName: String,
    val wifiDirect: WifiDirectCredentials?,
    val bluetooth: Boolean,
) {
    init {
        require(sessionId.matches(SESSION_ID)) { "Invalid session id" }
        require(secret.size == SECRET_BYTES) { "Invalid secret" }
        require(patientName.isNotBlank() && patientName.length <= MAX_NAME) { "Invalid name" }
        require(wifiDirect != null || bluetooth) { "At least one transport is needed" }
    }

    fun toUri(): String =
        buildString {
            append("suno://join?v=").append(VERSION)
            append("&id=").append(sessionId)
            append("&k=").append(B64.encodeToString(secret))
            append("&n=").append(enc(patientName))
            wifiDirect?.let { append("&w=").append(enc(it.networkName)).append("&p=").append(enc(it.passphrase)) }
            if (bluetooth) append("&b=1")
        }

    companion object {
        const val VERSION = 1
        const val SECRET_BYTES = 32
        private const val MAX_NAME = 40
        private const val PREFIX = "suno://join?"
        private val SESSION_ID = Regex("[0-9a-f]{16}")
        private val B64 = Base64.getUrlEncoder().withoutPadding()
        private val B64D = Base64.getUrlDecoder()
        private const val PASS_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"

        /** Parses a scanned code; returns null for anything that is not a valid Suno join code. */
        fun parse(text: String): JoinCode? {
            if (!text.startsWith(PREFIX)) return null
            val params =
                text
                    .removePrefix(PREFIX)
                    .split('&')
                    .mapNotNull { part ->
                        part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to dec(it[1]) }
                    }.toMap()
            return runCatching {
                require(params["v"] == VERSION.toString())
                val w = params["w"]
                val p = params["p"]
                JoinCode(
                    sessionId = params.getValue("id"),
                    secret = B64D.decode(params.getValue("k")),
                    patientName = params.getValue("n"),
                    wifiDirect = if (w != null && p != null) WifiDirectCredentials(w, p) else null,
                    bluetooth = params["b"] == "1",
                )
            }.getOrNull()
        }

        /** A new identity for a patient's Suno. Generated once and stored. */
        fun generate(
            patientName: String,
            random: SecureRandom = SecureRandom(),
        ): JoinCode {
            val id = ByteArray(8).also(random::nextBytes).joinToString("") { "%02x".format(it) }
            val secret = ByteArray(SECRET_BYTES).also(random::nextBytes)

            fun chars(n: Int) = (1..n).map { PASS_CHARS[random.nextInt(PASS_CHARS.length)] }.joinToString("")
            return JoinCode(
                sessionId = id,
                secret = secret,
                patientName = patientName,
                wifiDirect = WifiDirectCredentials("DIRECT-${chars(2)}-Suno", chars(16)),
                bluetooth = true,
            )
        }

        private fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8)

        private fun dec(s: String) = URLDecoder.decode(s, Charsets.UTF_8)
    }
}
