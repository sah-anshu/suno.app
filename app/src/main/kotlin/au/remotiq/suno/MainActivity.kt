package au.remotiq.suno

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import au.remotiq.suno.ui.SunoApp
import au.remotiq.suno.ui.patient.PatientRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Patient: simple layout. TV: Suno TV screen. Companion: EarCast layout with the Family tab.
            when (BuildConfig.FLAVOR) {
                "patient" -> PatientRoot()
                "tv" ->
                    au.remotiq.suno.ui
                        .TvEntry()
                else -> SunoApp()
            }
        }
    }
}
