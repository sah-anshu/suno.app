// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.ui.family

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import au.remotiq.suno.family.CompanionStatus
import au.remotiq.suno.link.ClientState
import au.remotiq.suno.ui.theme.SunoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the companion's Family tab in its main states (EarCast layout). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi")
class FamilyScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shot(
        name: String,
        status: CompanionStatus,
    ) {
        compose.setContent {
            SunoTheme(darkTheme = false) {
                FamilyContent(status, "Rahul", {}, {}, {}, {}, {})
            }
        }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun join() = shot("20-companion-join", CompanionStatus())

    @Test
    fun waiting() = shot("21-companion-waiting", CompanionStatus("Dadi", ClientState.WAITING_FOR_APPROVAL))

    @Test
    fun connected() =
        shot(
            "22-companion-connected",
            CompanionStatus("Dadi", ClientState.CONNECTED, au.remotiq.suno.link.android.LinkTransport.WIFI_DIRECT),
        )

    @Test
    fun talking() =
        shot(
            "23-companion-talking",
            CompanionStatus(
                "Dadi",
                ClientState.CONNECTED,
                au.remotiq.suno.link.android.LinkTransport.BLUETOOTH,
                talking = true,
            ),
        )

    @Test
    fun sharing() =
        shot(
            "24-companion-sharing",
            CompanionStatus(
                "Dadi",
                ClientState.CONNECTED,
                au.remotiq.suno.link.android.LinkTransport.WIFI_DIRECT,
                sharing = au.remotiq.suno.link.android.ShareSource.CABLE,
            ),
        )

    @Test
    fun settingUpTv() =
        shot(
            "25-companion-tv-setup",
            CompanionStatus(
                "Dadi",
                ClientState.CONNECTED,
                au.remotiq.suno.link.android.LinkTransport.WIFI_DIRECT,
                tvSetup = au.remotiq.suno.link.TvProvisioningClient.State.SENDING,
            ),
        )

    private val alert =
        au.remotiq.suno.link.SosAlert(
            id = 1_763_700_000_000,
            active = true,
            location =
                au.remotiq.suno.link
                    .GeoPoint(28.61394, 77.20902, 12f),
            responders = listOf("Nana"),
        )

    @Test
    fun sosAlert() =
        shot(
            "26-companion-sos",
            CompanionStatus(
                "Dadi",
                ClientState.CONNECTED,
                au.remotiq.suno.link.android.LinkTransport.WIFI_DIRECT,
                sos = alert,
                myName = "Rahul",
            ),
        )

    @Test
    fun sosEnded() =
        shot(
            "27-companion-sos-safe",
            CompanionStatus(
                "Dadi",
                ClientState.CONNECTED,
                au.remotiq.suno.link.android.LinkTransport.RELAY,
                sos = alert.copy(active = false),
                myName = "Rahul",
            ),
        )
}
