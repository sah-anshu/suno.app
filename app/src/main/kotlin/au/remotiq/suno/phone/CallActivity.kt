// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.phone

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import au.remotiq.suno.data.PatientSettings
import au.remotiq.suno.data.PreferenceStorage
import au.remotiq.suno.ui.patient.CallCallbacks
import au.remotiq.suno.ui.patient.CallScreen
import au.remotiq.suno.ui.patient.PatientTheme
import au.remotiq.suno.ui.patient.PatientThemeName
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Suno's call screen. Shows over the lock screen and closes when the call is over. */
@AndroidEntryPoint
class CallActivity : ComponentActivity() {
    @Inject
    lateinit var calls: PhoneCalls

    @Inject
    lateinit var preferences: PreferenceStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        enableEdgeToEdge()
        val callbacks =
            CallCallbacks(
                onAnswer = calls::answer,
                onDecline = calls::decline,
                onEnd = calls::end,
                onMute = calls::setMuted,
                onRoute = calls::setRoute,
                onVolume = calls::changeVolume,
            )
        setContent {
            val call by calls.call.collectAsState()
            val settings by preferences.observePatientSettings().collectAsState(PatientSettings())
            LaunchedEffect(call == null) { if (call == null) finish() }
            call?.let { PatientTheme(PatientThemeName.fromName(settings.theme)) { CallScreen(it, callbacks) } }
        }
    }
}
