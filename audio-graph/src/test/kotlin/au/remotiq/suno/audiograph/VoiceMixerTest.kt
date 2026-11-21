// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.audiograph

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VoiceMixerTest {
    private val rate = 48_000
    private val block = 192

    private fun talk(
        hub: RemoteAudioHub,
        frames: Int,
        start: Long = 0,
    ) {
        for (s in 0 until frames) {
            val seq = start + s
            val offset = (seq * RemoteAudioHub.FRAME_SAMPLES).toInt()
            hub.push("rahul", "Rahul", seq, sine(1_000.0, 16_000, RemoteAudioHub.FRAME_SAMPLES, 0.3, offset))
        }
    }

    private fun room(value: Short = 8_000) = ShortArray(block) { value }

    @Test
    fun `a person talking comes to the front and the room is turned down`() {
        val hub = RemoteAudioHub()
        val mixer = VoiceMixer(hub)
        talk(hub, 3)
        val out = mutableListOf<Float>()
        // One 20 ms frame arrives for every five 4 ms blocks, as on a live link.
        repeat(200) { i ->
            if (i % 5 == 0) talk(hub, 1, start = 3L + i / 5)
            val b = room(0)
            mixer.mix(b, block, rate)
            out += b.map { it / 32768f }
        }
        assertEquals(0.3, level(out.drop(9_600).toFloatArray(), 1_000.0, rate), 0.03)
        assertEquals(0.25f, mixer.roomGain, 0.01f)
        assertEquals(listOf("rahul"), hub.talkers.map { it.id })
    }

    @Test
    fun `room comes back when the person stops`() {
        val hub = RemoteAudioHub()
        val mixer = VoiceMixer(hub)
        talk(hub, 5)
        repeat(200) { mixer.mix(room(), block, rate) }
        assertEquals(1f, mixer.roomGain, 0.001f)
        assertTrue(hub.active.value.isEmpty())
    }

    @Test
    fun `person only silences the room and room only ignores voices`() {
        val hub = RemoteAudioHub()
        val mixer = VoiceMixer(hub).apply { mode = SourceMode.PERSON_ONLY }
        repeat(50) { mixer.mix(room(), block, rate) }
        val b = room()
        mixer.mix(b, block, rate)
        assertTrue(b.all { it == 0.toShort() })

        val roomOnly = VoiceMixer(hub).apply { mode = SourceMode.ROOM_ONLY }
        talk(hub, 20)
        val r = room(1_000)
        repeat(30) { roomOnly.mix(r.copyOf(), block, rate) }
        roomOnly.mix(r, block, rate)
        assertTrue(r.all { it == 1_000.toShort() })
    }

    @Test
    fun `loud voice plus loud room saturates instead of wrapping`() {
        val hub = RemoteAudioHub()
        for (s in 0L until 20L) hub.push("x", "X", s, FloatArray(RemoteAudioHub.FRAME_SAMPLES) { 1f })
        val mixer = VoiceMixer(hub)
        repeat(40) {
            val b = room(Short.MAX_VALUE)
            mixer.mix(b, block, rate)
            assertTrue(b.all { it > 0 }, "no wrap-around to negative values")
        }
    }

    private fun tv(
        hub: RemoteAudioHub,
        seq: Long,
    ) = hub.push("tv", "TV", seq, sine(400.0, 16_000, RemoteAudioHub.FRAME_SAMPLES, 0.3), StreamKind.MEDIA)

    @Test
    fun `TV sound turns the room down, and a person talking turns the TV down`() {
        val hub = RemoteAudioHub()
        val mixer = VoiceMixer(hub)
        (0L until 3L).forEach { tv(hub, it) }
        repeat(100) { i ->
            if (i % 5 == 0) tv(hub, 3L + i / 5)
            mixer.mix(room(), block, rate)
        }
        assertEquals(0.25f, mixer.roomGain, 0.01f)
        assertEquals(1f, mixer.mediaGain, 0.01f)
        assertTrue(hub.talkers.isEmpty(), "TV is not a person talking")
        assertEquals(listOf(StreamKind.MEDIA), hub.active.value.map { it.kind })

        talk(hub, 3, start = 0)
        repeat(100) { i ->
            if (i % 5 == 0) {
                tv(hub, 23L + i / 5)
                talk(hub, 1, start = 3L + i / 5)
            }
            mixer.mix(room(), block, rate)
        }
        assertEquals(0.25f, mixer.mediaGain, 0.01f)
    }

    @Test
    fun `TV only silences the room but still lets a person through`() {
        val hub = RemoteAudioHub()
        val mixer = VoiceMixer(hub).apply { mode = SourceMode.TV_ONLY }
        repeat(50) { mixer.mix(room(), block, rate) }
        assertEquals(0f, mixer.roomGain, 0.001f)
        assertEquals(1f, mixer.mediaGain, 0.001f)
    }
}
