// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.family

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import au.remotiq.suno.MainActivity
import au.remotiq.suno.R
import au.remotiq.suno.link.ClientState
import au.remotiq.suno.link.SosAlert
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the companion linked to the patient's Suno in the background, so an SOS reaches this phone
 * even when Suno Companion is closed. Shows the SOS as a full-screen alert. Runs while a patient
 * is saved on this phone; Leave in the Family tab ends it.
 */
@AndroidEntryPoint
class FamilyLinkService : Service() {
    @Inject
    lateinit var link: CompanionLink

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false

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
        createChannels()
        val ok =
            runCatching {
                ServiceCompat.startForeground(this, LINK_ID, linkNotification(link.status.value), type())
            }.isSuccess
        if (!ok) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            scope.launch {
                link.status.map { it.state to it.patientName }.distinctUntilChanged().collect {
                    manager().notify(LINK_ID, linkNotification(link.status.value))
                }
            }
            scope.launch {
                link.status
                    .map { it.sos }
                    .distinctUntilChanged()
                    .collect(::showSos)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        manager().cancel(SOS_ID)
        super.onDestroy()
    }

    private fun manager() = getSystemService(NotificationManager::class.java)

    private fun type(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(
                this,
                MainActivity::class.java,
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun linkNotification(status: CompanionStatus) =
        NotificationCompat
            .Builder(this, LINK_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_assist)
            .setContentTitle(
                getString(
                    if (status.state ==
                        ClientState.CONNECTED
                    ) {
                        R.string.link_notice_connected
                    } else {
                        R.string.link_notice_waiting
                    },
                    status.patientName.orEmpty(),
                ),
            ).setContentText(getString(R.string.link_notice_text))
            .setOngoing(true)
            .setContentIntent(openApp())
            .build()

    private fun showSos(alert: SosAlert?) {
        val name =
            link.status.value.patientName
                .orEmpty()
        when {
            alert == null -> manager().cancel(SOS_ID)
            alert.active ->
                manager().notify(
                    SOS_ID,
                    NotificationCompat
                        .Builder(this, SOS_CHANNEL)
                        .setSmallIcon(R.drawable.ic_stat_assist)
                        .setContentTitle(getString(R.string.sos_alert_title, name))
                        .setContentText(getString(R.string.sos_alert_text))
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setFullScreenIntent(openApp(), true)
                        .setContentIntent(openApp())
                        .setOngoing(true)
                        .build(),
                )
            else ->
                manager().notify(
                    SOS_ID,
                    NotificationCompat
                        .Builder(this, SOS_CHANNEL)
                        .setSmallIcon(R.drawable.ic_stat_assist)
                        .setContentTitle(getString(R.string.sos_alert_safe, name))
                        .setContentIntent(openApp())
                        .setAutoCancel(true)
                        .build(),
                )
        }
    }

    private fun createChannels() {
        manager().createNotificationChannel(
            NotificationChannel(
                LINK_CHANNEL,
                getString(R.string.link_notice_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        manager().createNotificationChannel(
            NotificationChannel(
                SOS_CHANNEL,
                getString(R.string.sos_alert_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    companion object {
        private const val LINK_CHANNEL = "family_link"
        private const val SOS_CHANNEL = "sos_alerts"
        private const val LINK_ID = 21
        private const val SOS_ID = 22
        private const val ACTION_STOP = "au.remotiq.suno.family.LINK_STOP"

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, FamilyLinkService::class.java),
                )
            }
        }

        fun stop(context: Context) {
            runCatching { context.startService(Intent(context, FamilyLinkService::class.java).setAction(ACTION_STOP)) }
        }
    }
}
