// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.tv

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import au.remotiq.suno.link.ClientState
import au.remotiq.suno.link.android.LinkTransport
import au.remotiq.suno.ui.patient.PatientTheme
import au.remotiq.suno.ui.patient.PatientThemeName
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the Suno TV screen in its main states at 1080p TV size. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
class TvScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shot(
        name: String,
        status: TvStatus,
        notAllowed: Boolean = false,
    ) {
        compose.setContent {
            PatientTheme(PatientThemeName.DARK) {
                TvScreen(status, notAllowed, {}, {}, {}, {})
            }
        }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun setup() =
        shot("30-tv-setup", TvStatus(setupUri = "suno://join?v=1&id=0123456789abcdef&k=secret&n=Living%20room&t=1"))

    @Test
    fun waiting() = shot("31-tv-waiting", TvStatus(patientName = "Dadi", state = ClientState.WAITING_FOR_APPROVAL))

    @Test
    fun connected() =
        shot(
            "32-tv-connected",
            TvStatus(patientName = "Dadi", state = ClientState.CONNECTED, transport = LinkTransport.WIFI_DIRECT),
        )

    @Test
    fun sharing() =
        shot(
            "33-tv-sharing",
            TvStatus(
                patientName = "Dadi",
                state = ClientState.CONNECTED,
                transport = LinkTransport.WIFI_DIRECT,
                sharing = true,
            ),
        )

    @Test
    fun notConnected() = shot("34-tv-not-connected", TvStatus(patientName = "Dadi", state = ClientState.CLOSED))

    @Test
    fun sos() =
        shot(
            "35-tv-sos",
            TvStatus(
                patientName = "Dadi",
                state = ClientState.CONNECTED,
                transport = LinkTransport.WIFI_DIRECT,
                sharing = true,
                sosActive = true,
            ),
        )
}
