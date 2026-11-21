// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.phone

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import androidx.core.app.NotificationCompat
import au.remotiq.suno.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Bound by Android while Suno is the phone app and a call exists. Shows Suno's call screen: large
 * Answer and Decline buttons, no swiping. Android plays the ringtone and routes the call sound.
 */
@AndroidEntryPoint
class SunoInCallService : InCallService() {
    @Inject
    lateinit var calls: PhoneCalls

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        calls.onServiceBound(this)
    }

    override fun onCallAdded(call: Call) {
        scope.launch {
            if (!calls.onCallAdded(call)) return@launch
            val incoming = call.details.callDirection == Call.Details.DIRECTION_INCOMING
            if (incoming) showIncoming() else openScreen()
        }
    }

    override fun onCallRemoved(call: Call) {
        calls.onCallRemoved(call)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    @Deprecated("Deprecated in Android 14, still delivered")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        calls.onAudioState(audioState)
    }

    override fun onDestroy() {
        calls.onServiceBound(null)
        scope.cancel()
        super.onDestroy()
    }

    private fun screenIntent() =
        Intent(
            this,
            CallActivity::class.java,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    private fun openScreen() {
        runCatching { startActivity(screenIntent()) }
    }

    /** A full-screen notification opens the call screen even when the phone is locked. */
    private fun showIncoming() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.call_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                setSound(null, null)
            },
        )
        val open = PendingIntent.getActivity(this, 0, screenIntent(), PendingIntent.FLAG_IMMUTABLE)
        val name =
            calls.call.value
                ?.caller
                ?.let { it.name ?: it.number }
                .orEmpty()
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_assist)
                .setContentTitle(getString(R.string.call_incoming_title, name))
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setFullScreenIntent(open, true)
                .setContentIntent(open)
                .setOngoing(true)
                .build(),
        )
        openScreen()
    }

    private companion object {
        const val CHANNEL_ID = "calls"
        const val NOTIFICATION_ID = 31
    }
}
