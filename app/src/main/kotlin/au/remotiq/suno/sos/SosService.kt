// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.sos

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import au.remotiq.suno.MainActivity
import au.remotiq.suno.R

/**
 * Keeps Suno running while SOS is on (the phone app is in front during the call), so family phones
 * keep getting the alert and the place. Ends only from the SOS screen.
 */
class SosService : Service() {
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
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.sos_channel), NotificationManager.IMPORTANCE_HIGH),
        )
        val open =
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.sos_notification_title))
                .setContentText(getString(R.string.sos_notification_text))
                .setSmallIcon(R.drawable.ic_stat_assist)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(open)
                .build()
        val started = runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type()) }
        if (started.isFailure) stopSelf()
        return START_NOT_STICKY
    }

    private fun type(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return 0
        val location =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val device = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        return if (location) device or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else device
    }

    companion object {
        private const val CHANNEL_ID = "sos"
        private const val NOTIFICATION_ID = 7
        private const val ACTION_STOP = "au.remotiq.suno.sos.STOP"

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, SosService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.startService(Intent(context, SosService::class.java).setAction(ACTION_STOP)) }
        }
    }
}
