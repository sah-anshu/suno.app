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
            // The patient app opens the simple layout; companion and TV keep EarCast's screens until their phases.
            if (BuildConfig.FLAVOR == "patient") PatientRoot() else SunoApp()
        }
    }
}
