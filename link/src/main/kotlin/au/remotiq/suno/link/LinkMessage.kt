// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.link

import java.nio.ByteBuffer

/** Messages exchanged after a connection's keys are set up. Encoded, then sealed with [LinkCrypto]. */
sealed interface LinkMessage {
    /** Companion → patient: who is asking, and which codecs it can send. */
    data class Hello(
        val version: Int,
        val clientId: String,
        val deviceName: String,
        val codecs: List<Int>,
    ) : LinkMessage

    /** Patient → companion: waiting for the patient's phone to allow the join. */
    data object Pending : LinkMessage

    /** Patient → companion: allowed. Audio frames carry [audioTag] and use [codec]. */
    data class Welcome(
        val codec: Int,
        val audioTag: Int,
    ) : LinkMessage

    /** Patient → companion: not allowed. */
    data object Denied : LinkMessage

    /** Either side: leaving. */
    data object Bye : LinkMessage

    /** Companion → patient: one encoded 20 ms frame. */
    class Audio(
        val sequence: Long,
        val codec: Int,
        val payload: ByteArray,
    ) : LinkMessage

    companion object {
        private const val HELLO: Byte = 1
        private const val PENDING: Byte = 2
        private const val WELCOME: Byte = 3
        private const val DENIED: Byte = 4
        private const val BYE: Byte = 5
        private const val AUDIO: Byte = 6
        private const val MAX_TEXT = 200

        fun encode(message: LinkMessage): ByteArray =
            when (message) {
                is Hello -> {
                    val id = message.clientId.toByteArray()
                    val name = message.deviceName.take(MAX_TEXT).toByteArray()
                    ByteBuffer
                        .allocate(1 + 1 + 2 + id.size + 2 + name.size + 1 + message.codecs.size)
                        .put(HELLO)
                        .put(message.version.toByte())
                        .putShort(id.size.toShort())
                        .put(id)
                        .putShort(name.size.toShort())
                        .put(name)
                        .put(message.codecs.size.toByte())
                        .apply { message.codecs.forEach { put(it.toByte()) } }
                        .array()
                }
                Pending -> byteArrayOf(PENDING)
                is Welcome ->
                    ByteBuffer
                        .allocate(
                            1 + 1 + 4,
                        ).put(WELCOME)
                        .put(message.codec.toByte())
                        .putInt(message.audioTag)
                        .array()
                Denied -> byteArrayOf(DENIED)
                Bye -> byteArrayOf(BYE)
                is Audio ->
                    ByteBuffer
                        .allocate(1 + 8 + 1 + message.payload.size)
                        .put(AUDIO)
                        .putLong(message.sequence)
                        .put(message.codec.toByte())
                        .put(message.payload)
                        .array()
            }

        /** Returns null for anything malformed. */
        fun decode(bytes: ByteArray): LinkMessage? =
            runCatching {
                val b = ByteBuffer.wrap(bytes)
                when (b.get()) {
                    HELLO -> {
                        val version = b.get().toInt()
                        val id = String(ByteArray(b.short.toInt()).also { b.get(it) })
                        val name = String(ByteArray(b.short.toInt()).also { b.get(it) })
                        val codecs = List(b.get().toInt()) { b.get().toInt() }
                        Hello(version, id, name, codecs)
                    }
                    PENDING -> Pending
                    WELCOME -> Welcome(b.get().toInt(), b.int)
                    DENIED -> Denied
                    BYE -> Bye
                    AUDIO -> Audio(b.long, b.get().toInt(), ByteArray(b.remaining()).also { b.get(it) })
                    else -> null
                }
            }.getOrNull()
    }
}
