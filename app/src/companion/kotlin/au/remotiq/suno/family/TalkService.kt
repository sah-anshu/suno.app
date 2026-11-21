// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.family

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import au.remotiq.suno.R
import au.remotiq.suno.audiograph.RemoteAudioHub
import au.remotiq.suno.link.ClientState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.concurrent.thread

/**
 * Captures the companion's voice (with the phone's echo cancellation and noise suppression) and
 * sends it to the patient in 20 ms frames while Talk is on. A foreground service so talking keeps
 * working with the screen off; Stop is always in the notification.
 */
@AndroidEntryPoint
class TalkService : Service() {
    @Inject
    lateinit var link: CompanionLink

    @Volatile private var running = false
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundNotification()
        if (!running) {
            running = true
            link.setTalking(true)
            worker = thread(name = "suno-talk") { capture() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        worker?.join(STOP_JOIN_MS)
        link.setTalking(false)
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    private fun capture() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val frame = RemoteAudioHub.FRAME_SAMPLES
        val minBuffer =
            AudioRecord.getMinBufferSize(
                RemoteAudioHub.STREAM_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        val record =
            runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    RemoteAudioHub.STREAM_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuffer, frame * 2 * 4),
                )
            }.getOrNull()
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            record?.release()
            stopSelf()
            return
        }
        val buffer = ShortArray(frame)
        try {
            record.startRecording()
            while (running && link.status.value.state == ClientState.CONNECTED) {
                var filled = 0
                while (filled < frame && running) {
                    val read = record.read(buffer, filled, frame - filled)
                    if (read <= 0) break
                    filled += read
                }
                if (filled == frame) link.sendFrame(buffer.copyOf())
            }
        } finally {
            runCatching { record.stop() }
            record.release()
            stopSelf()
        }
    }

    private fun startForegroundNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.talk_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val stop =
            PendingIntent.getService(
                this,
                0,
                Intent(this, TalkService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val notification: Notification =
            NotificationCompat
                .Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_assist)
                .setContentTitle(getString(R.string.talk_notification))
                .setOngoing(true)
                .addAction(0, getString(R.string.talk_stop), stop)
                .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL = "talk"
        private const val NOTIFICATION_ID = 2
        private const val STOP_JOIN_MS = 500L
        private const val ACTION_STOP = "au.remotiq.suno.family.STOP_TALK"

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, TalkService::class.java))

        fun stop(context: Context) {
            context.startService(Intent(context, TalkService::class.java).setAction(ACTION_STOP))
        }
    }
}
