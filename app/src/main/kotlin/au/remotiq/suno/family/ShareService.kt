// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.family

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import au.remotiq.suno.R
import au.remotiq.suno.audiograph.RemoteAudioHub
import au.remotiq.suno.link.android.ShareSource
import au.remotiq.suno.link.android.SoundCapture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where shared sound goes: the companion's or the TV's link to the patient. */
interface ShareSink {
    /** True while connected to the patient's phone; sharing stops when this turns false. */
    val connected: StateFlow<Boolean>

    fun sendMedia(frame: ShortArray)

    fun onSharing(source: ShareSource?)
}

/**
 * Shares sound with the patient: a TV by cable, a microphone near the TV, or what this device is
 * playing. A foreground service so it keeps going with the screen off; Stop is in the notification.
 * Declared only in the companion and TV apps.
 */
@AndroidEntryPoint
class ShareService : Service() {
    @Inject
    lateinit var sink: ShareSink

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var capture: SoundCapture? = null
    private var projection: MediaProjection? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val source =
            intent?.getStringExtra(EXTRA_SOURCE)?.let { name ->
                ShareSource.entries.firstOrNull {
                    it.name ==
                        name
                }
            }
        if (intent?.action == ACTION_STOP || source == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        capture?.stop()
        startForegroundFor(source)
        if (source == ShareSource.PLAYBACK) projection = openProjection(intent)
        val started =
            SoundCapture(this, RemoteAudioHub.FRAME_SAMPLES).let { c ->
                capture = c
                c.start(source, projection, sink::sendMedia)
            }
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        sink.onSharing(source)
        scope.launch {
            sink.connected.first { !it }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        capture?.stop()
        projection?.stop()
        sink.onSharing(null)
        scope.cancel()
        super.onDestroy()
    }

    private fun openProjection(intent: Intent): MediaProjection? {
        val data = IntentCompat.getParcelableExtra(intent, EXTRA_DATA, Intent::class.java) ?: return null
        val code = intent.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED)
        val manager = getSystemService(MediaProjectionManager::class.java)
        return runCatching { manager.getMediaProjection(code, data) }.getOrNull()?.also {
            it.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() = stopSelf()
                },
                Handler(Looper.getMainLooper()),
            )
        }
    }

    private fun startForegroundFor(source: ShareSource) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.share_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val stop =
            PendingIntent.getService(
                this,
                0,
                Intent(this, ShareService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_assist)
                .setContentTitle(getString(R.string.share_notification))
                .setOngoing(true)
                .addAction(0, getString(R.string.share_stop), stop)
                .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type =
                if (source == ShareSource.PLAYBACK) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL = "share"
        private const val NOTIFICATION_ID = 3
        private const val ACTION_STOP = "au.remotiq.suno.family.STOP_SHARE"
        private const val EXTRA_SOURCE = "source"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"

        /** Starts sharing from a cable or microphone. */
        fun start(
            context: Context,
            source: ShareSource,
        ) = ContextCompat.startForegroundService(
            context,
            Intent(context, ShareService::class.java).putExtra(EXTRA_SOURCE, source.name),
        )

        /** Starts sharing this device's playback, with the screen-capture consent result. */
        fun startPlayback(
            context: Context,
            resultCode: Int,
            data: Intent,
        ) = ContextCompat.startForegroundService(
            context,
            Intent(context, ShareService::class.java)
                .putExtra(EXTRA_SOURCE, ShareSource.PLAYBACK.name)
                .putExtra(EXTRA_CODE, resultCode)
                .putExtra(EXTRA_DATA, data),
        )

        fun stop(context: Context) {
            context.startService(Intent(context, ShareService::class.java).setAction(ACTION_STOP))
        }
    }
}
