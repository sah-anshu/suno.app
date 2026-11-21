// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.sos

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * A two-tone alarm on the phone's own speaker so people nearby hear it. It never plays into
 * earphones or hearing aids: if the built-in speaker cannot be chosen, it stays silent (fail quiet).
 * This is an alert tone, not amplified sound.
 */
class SosAlarm(
    private val context: Context,
) {
    private var track: AudioTrack? = null

    fun start(): Boolean {
        if (track != null) return true
        val audio = context.getSystemService(AudioManager::class.java)
        val speaker =
            audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            } ?: return false
        val samples = siren()
        val created =
            AudioTrack
                .Builder()
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                ).setAudioFormat(
                    AudioFormat
                        .Builder()
                        .setSampleRate(RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                ).setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()
        if (!created.setPreferredDevice(speaker)) {
            created.release()
            return false
        }
        created.write(samples, 0, samples.size)
        created.setLoopPoints(0, samples.size, -1)
        created.play()
        track = created
        return true
    }

    fun stop() {
        track?.let {
            runCatching { it.stop() }
            it.release()
        }
        track = null
    }

    /** Half a second high, half a second low; looped. */
    private fun siren(): ShortArray =
        ShortArray(RATE) { i ->
            val hz = if (i < RATE / 2) HIGH_HZ else LOW_HZ
            (LEVEL * sin(2 * PI * hz * i / RATE)).toInt().toShort()
        }

    private companion object {
        const val RATE = 22_050
        const val HIGH_HZ = 1_400.0
        const val LOW_HZ = 900.0
        const val LEVEL = 24_000.0
    }
}
