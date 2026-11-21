// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.link.android

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import kotlin.concurrent.thread

/** Where shared sound comes from. */
enum class ShareSource {
    /** A cable from the TV's headphone or line output into a USB-C (or wired) audio input. */
    CABLE,

    /** This device's microphone, placed near the TV or speaker. */
    MICROPHONE,

    /** Sound that apps on this device are playing (Android 10+, needs screen-capture consent). */
    PLAYBACK,
}

/**
 * Captures 20 ms, 16 kHz mono frames for sharing. No processing is applied: the patient's phone
 * applies the patient's own profile. Apps that opt out of playback capture are silent in PLAYBACK.
 */
class SoundCapture(
    private val context: Context,
    private val frameSamples: Int,
    private val sampleRateHz: Int = 16_000,
) {
    @Volatile private var running = false
    private var worker: Thread? = null

    /** Starts capturing; [onFrame] runs on a capture thread. Returns false if the source could not open. */
    @SuppressLint("MissingPermission")
    fun start(
        source: ShareSource,
        projection: MediaProjection?,
        onFrame: (ShortArray) -> Unit,
    ): Boolean {
        val record = open(source, projection) ?: return false
        running = true
        worker =
            thread(name = "suno-share") {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                val buffer = ShortArray(frameSamples)
                try {
                    record.startRecording()
                    while (running) {
                        var filled = 0
                        while (filled < frameSamples && running) {
                            val read = record.read(buffer, filled, frameSamples - filled)
                            if (read <= 0) break
                            filled += read
                        }
                        if (filled == frameSamples) onFrame(buffer.copyOf())
                    }
                } finally {
                    runCatching { record.stop() }
                    record.release()
                }
            }
        return true
    }

    fun stop() {
        running = false
        worker?.join(STOP_JOIN_MS)
        worker = null
    }

    @SuppressLint("MissingPermission")
    private fun open(
        source: ShareSource,
        projection: MediaProjection?,
    ): AudioRecord? {
        val format =
            AudioFormat
                .Builder()
                .setSampleRate(sampleRateHz)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()
        val bytes =
            maxOf(
                AudioRecord.getMinBufferSize(sampleRateHz, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT),
                frameSamples * 8,
            )
        val record =
            runCatching {
                when (source) {
                    ShareSource.PLAYBACK -> playbackRecord(checkNotNull(projection), format, bytes)
                    ShareSource.CABLE, ShareSource.MICROPHONE ->
                        AudioRecord
                            .Builder()
                            // Unprocessed keeps music and TV sound intact (no echo cancellation or noise gating).
                            .setAudioSource(rawSource())
                            .setAudioFormat(format)
                            .setBufferSizeInBytes(bytes)
                            .build()
                            .also { if (source == ShareSource.CABLE) cableInput()?.let(it::setPreferredDevice) }
                }
            }.getOrNull()
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            record?.release()
            return null
        }
        return record
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    private fun playbackRecord(
        projection: MediaProjection,
        format: AudioFormat,
        bytes: Int,
    ): AudioRecord {
        val config =
            AudioPlaybackCaptureConfiguration
                .Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
        return AudioRecord
            .Builder()
            .setAudioPlaybackCaptureConfig(config)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bytes)
            .build()
    }

    private fun rawSource(): Int {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val unprocessed = audio.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        return if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.MIC
    }

    /** A USB or wired audio input, if one is plugged in. */
    fun cableInput(): AudioDeviceInfo? {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audio.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.type in CABLE_TYPES }
    }

    private companion object {
        const val STOP_JOIN_MS = 500L
        val CABLE_TYPES =
            setOf(
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_LINE_ANALOG,
                AudioDeviceInfo.TYPE_LINE_DIGITAL,
            )
    }
}
