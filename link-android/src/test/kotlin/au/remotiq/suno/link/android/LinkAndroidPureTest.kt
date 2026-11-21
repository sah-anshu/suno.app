// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.link.android

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class LinkAndroidPureTest {
    @Test
    fun `advert carries the session id and channel, and ignores other sessions`() {
        val id = "0123456789abcdef"
        val data = BluetoothAdvert.encode(id, 0x00C5)
        assertEquals(11, data.size)
        assertEquals(BluetoothAdvert.Target(0x00C5, relay = false), BluetoothAdvert.targetFor(id, data))
        assertNull(BluetoothAdvert.targetFor("fedcba9876543210", data))
        assertNull(BluetoothAdvert.targetFor(id, byteArrayOf(1, 2)))
        assertEquals(data.size, BluetoothAdvert.ID_MASK.size)
    }

    @Test
    fun `a relay's advert is marked, and an advert without flags is the patient's own`() {
        val id = "0123456789abcdef"
        assertTrue(BluetoothAdvert.targetFor(id, BluetoothAdvert.encode(id, 0x0081, relay = true))!!.relay)
        val old = BluetoothAdvert.encode(id, 0x0081).copyOf(10)
        assertEquals(BluetoothAdvert.Target(0x0081, relay = false), BluetoothAdvert.targetFor(id, old))
    }

    @Test
    fun `decimator keeps speech and removes content above 8 kHz`() {
        val d = Decimator3()

        fun tone(hz: Double) = FloatArray(48_000) { (0.5 * sin(2 * PI * hz * it / 48_000)).toFloat() }
        val speech = d.process(tone(1_000.0)).copyOfRange(200, 15_800)
        val rms = sqrt(speech.sumOf { it.toDouble() * it } / speech.size)
        assertEquals(0.5 / sqrt(2.0), rms, 0.02)
        val high = Decimator3().process(tone(12_000.0)).copyOfRange(200, 15_800)
        assertTrue(high.maxOf { abs(it) } < 0.05, "12 kHz must not alias into the 16 kHz stream")
        assertEquals(16_000, Decimator3().process(FloatArray(48_000)).size)
    }
}
