// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.link

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.SecureRandom

class JoinCodeAndCryptoTest {
    @Test
    fun `join code round-trips, including a Hindi name`() {
        val code = JoinCode.generate("दादी")
        val parsed = JoinCode.parse(code.toUri())!!
        assertEquals(code.sessionId, parsed.sessionId)
        assertArrayEquals(code.secret, parsed.secret)
        assertEquals("दादी", parsed.patientName)
        assertEquals(code.wifiDirect, parsed.wifiDirect)
        assertTrue(parsed.bluetooth)
        assertTrue(parsed.wifiDirect!!.networkName.startsWith("DIRECT-"))
    }

    @Test
    fun `anything that is not a valid Suno code is rejected`() {
        val uri = JoinCode.generate("Dadi").toUri()
        assertNull(JoinCode.parse("https://example.com"))
        assertNull(JoinCode.parse(uri.replace("v=1", "v=9")))
        assertNull(JoinCode.parse(uri.replace(Regex("k=[^&]+"), "k=abc")))
        assertNull(JoinCode.parse(uri.replace(Regex("id=[^&]+"), "id=xyz")))
    }

    @Test
    fun `hkdf matches the RFC 5869 test vector`() {
        val ikm = ByteArray(22) { 0x0b }
        val salt = ByteArray(13) { it.toByte() }
        val info = ByteArray(10) { (0xf0 + it).toByte() }
        val okm = LinkCrypto.hkdf(ikm, salt, info, 42).joinToString("") { "%02x".format(it) }
        assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865", okm)
    }

    @Test
    fun `frames open only with the right key, once, and unmodified`() {
        val secret = ByteArray(32).also(SecureRandom()::nextBytes)
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val sender = LinkCrypto(secret, salt)
        val receiver = LinkCrypto(secret, salt)
        val sealed = sender.seal(Lane.CLIENT_CONTROL, "hello".toByteArray())
        assertEquals("hello", String(receiver.open(Lane.CLIENT_CONTROL, sealed)!!))
        assertNull(receiver.open(Lane.CLIENT_CONTROL, sealed), "replay rejected")
        assertNull(
            LinkCrypto(ByteArray(32), salt).open(Lane.CLIENT_CONTROL, sender.seal(Lane.CLIENT_CONTROL, byteArrayOf(1))),
        )
        val tampered =
            sender.seal(Lane.CLIENT_CONTROL, byteArrayOf(1, 2, 3)).also {
                it[it.size - 1] =
                    (it.last() + 1).toByte()
            }
        assertNull(receiver.open(Lane.CLIENT_CONTROL, tampered))
        assertNull(
            receiver.open(Lane.HOST_CONTROL, sender.seal(Lane.CLIENT_CONTROL, byteArrayOf(1))),
            "lanes do not mix",
        )
    }

    @Test
    fun `audio may arrive out of order within the window`() {
        val secret = ByteArray(32)
        val salt = ByteArray(16)
        val sender = LinkCrypto(secret, salt)
        val receiver = LinkCrypto(secret, salt)
        val frames = (1..5).map { sender.seal(Lane.CLIENT_AUDIO, byteArrayOf(it.toByte())) }
        listOf(1, 0, 4, 2, 3).forEach { assertNotNull(receiver.open(Lane.CLIENT_AUDIO, frames[it])) }
        assertNull(receiver.open(Lane.CLIENT_AUDIO, frames[2]))
    }

    @Test
    fun `messages round-trip`() {
        val hello = LinkMessage.Hello(1, "id-1", "Rahul's phone", listOf(CodecIds.PCM16, CodecIds.OPUS))
        assertEquals(hello, LinkMessage.decode(LinkMessage.encode(hello)))
        assertEquals(LinkMessage.Welcome(2, -7), LinkMessage.decode(LinkMessage.encode(LinkMessage.Welcome(2, -7))))
        val audio =
            LinkMessage.decode(
                LinkMessage.encode(LinkMessage.Audio(42, 1, byteArrayOf(9, 8))),
            ) as LinkMessage.Audio
        assertEquals(42, audio.sequence)
        assertArrayEquals(byteArrayOf(9, 8), audio.payload)
        assertNull(LinkMessage.decode(byteArrayOf(99)))
    }
}
