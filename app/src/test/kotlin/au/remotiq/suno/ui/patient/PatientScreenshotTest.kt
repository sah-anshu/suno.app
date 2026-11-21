// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.ui.patient

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import au.remotiq.suno.audiograph.SourceMode
import au.remotiq.suno.audiograph.Talker
import au.remotiq.suno.data.PatientSettings
import au.remotiq.suno.link.CompanionDevice
import au.remotiq.suno.link.JoinCode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the patient screens and saves PNGs to app/build/screenshots, so the real UI can be
 * reviewed without a device. Each test also checks that the screen composes without crashing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE)
class PatientScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shot(
        name: String,
        content: @Composable () -> Unit,
    ) {
        compose.setContent(content)
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun state(
        running: Boolean = false,
        settings: PatientSettings = PatientSettings(pinHash = "set"),
    ) = PatientUiState(loaded = true, settings = settings, running = running, consentAccepted = true)

    @Composable
    private fun Home(
        state: PatientUiState,
        message: String? = null,
        placeSheet: Boolean = false,
    ) {
        PatientTheme(state.theme) {
            PatientHomeScreen(
                state = state,
                device = DeviceStatus(earphones = true, batteryPercent = 80),
                placeSheetOpen = placeSheet,
                onListen = {},
                onVolume = {},
                onOpenPlaces = {},
                onClosePlaces = {},
                onPlace = {},
                onHelper = {},
                message = message,
            )
        }
    }

    @Test
    fun homeStandardOff() {
        shot("01-home-standard-off") { Home(state()) }
        compose.onNodeWithContentDescription("Helper").assertExists()
    }

    @Test
    fun homeStandardListening() = shot("02-home-standard-listening") { Home(state(running = true)) }

    @Test
    fun homeFullLevelWords() =
        shot("03-home-full-words") {
            Home(
                state(running = true, settings = PatientSettings(homeLevel = 3, picturesMode = false, pinHash = "set")),
            )
        }

    @Test
    fun homeSimplestDark() =
        shot("04-home-simplest-dark") {
            Home(state(running = true, settings = PatientSettings(homeLevel = 1, theme = "DARK", pinHash = "set")))
        }

    @Test
    fun homeYellowOnBlack() =
        shot("05-home-yellow") {
            Home(state(running = true, settings = PatientSettings(theme = "YELLOW", pinHash = "set")))
        }

    @Test
    @Config(qualifiers = "hi-$PHONE")
    fun homeHindi() =
        shot("06-home-hindi") {
            Home(state(running = true, settings = PatientSettings(homeLevel = 3, pinHash = "set")))
        }

    @Test
    fun placePicker() = shot("07-place-picker") { Home(state(), placeSheet = true) }

    @Test
    fun earphonesMessage() =
        shot("08-plug-in-message") {
            Home(state(), message = "Suno only plays through earphones, to keep your ears safe.")
        }

    @Test
    fun pinKeypad() =
        shot("09-helper-pin") {
            PatientTheme(PatientThemeName.STANDARD) { PinScreen(setMode = false, onComplete = { false }, onBack = {}) }
        }

    private val rahul = CompanionDevice("rahul-1", "Rahul")

    @Test
    fun joinRequest() =
        shot("10-join-request") {
            PatientTheme(PatientThemeName.STANDARD) { JoinApprovalScreen(rahul, {}, {}) }
        }

    @Test
    fun personTalkingWithNowHearing() =
        shot("11-person-talking") {
            Home(
                state(running = true).copy(
                    companions = listOf(rahul),
                    remote = listOf(Talker("rahul-1", "Rahul")),
                    sourceMode = SourceMode.AUTO,
                ),
            )
        }

    @Test
    @Config(qualifiers = "hi-$PHONE")
    fun personTalkingHindiNotListening() =
        shot("12-person-talking-hindi") {
            Home(
                state(settings = PatientSettings(homeLevel = 3, pinHash = "set")).copy(
                    companions = listOf(CompanionDevice("r", "राहुल")),
                    remote = listOf(Talker("r", "राहुल")),
                    sourceMode = SourceMode.PERSON_ONLY,
                ),
            )
        }

    @Test
    fun joinQrCode() =
        shot("13-join-qr") {
            PatientTheme(PatientThemeName.STANDARD) { QrCode(JoinCode.generate("Dadi").toUri(), "Join code") }
        }

    private val tv = CompanionDevice("tv-1", "Living room TV", au.remotiq.suno.link.DeviceKinds.TV)

    @Test
    fun tvJoinRequest() =
        shot("14-tv-join-request") {
            PatientTheme(PatientThemeName.STANDARD) { JoinApprovalScreen(tv, {}, {}) }
        }

    @Test
    fun tvPlaying() =
        shot("15-tv-playing") {
            Home(
                state(running = true).copy(
                    companions = listOf(tv, rahul),
                    remote = listOf(Talker("tv-1", "Living room TV", au.remotiq.suno.audiograph.StreamKind.MEDIA)),
                ),
            )
        }
}

private const val PHONE = "w400dp-h860dp-xxhdpi"
