@file:Suppress("ktlint:standard:function-naming")

package au.remotiq.suno.ui.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import au.remotiq.suno.audiogram.HearingCurve
import au.remotiq.suno.audiogram.HearingPoint
import au.remotiq.suno.common.AudioEar
import au.remotiq.suno.common.FrequencyHz
import au.remotiq.suno.common.HearingDb
import au.remotiq.suno.ui.AppState
import au.remotiq.suno.ui.assist.AmplificationCard
import au.remotiq.suno.ui.assist.ListenState
import au.remotiq.suno.ui.common.EarPage
import au.remotiq.suno.ui.media.MediaScreen
import au.remotiq.suno.ui.navigation.PrimaryDestination
import au.remotiq.suno.ui.navigation.navigationBarContent
import au.remotiq.suno.ui.onboarding.OnboardingScreen
import au.remotiq.suno.ui.profile.ProfileSetupScreen
import au.remotiq.suno.ui.theme.SunoTheme

// Fictional data is confined to IDE previews. Production screens use the active saved profile.
private fun previewAudiogram(): HearingCurve =
    HearingCurve(
        listOf(250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0).flatMapIndexed { index, frequency ->
            listOf(
                HearingPoint(
                    AudioEar.RIGHT,
                    FrequencyHz(frequency),
                    HearingDb(listOf(10.0, 15.0, 15.0, 30.0, 40.0, 35.0)[index]),
                ),
                HearingPoint(
                    AudioEar.LEFT,
                    FrequencyHz(frequency),
                    HearingDb(listOf(15.0, 10.0, 20.0, 25.0, 30.0, 40.0)[index]),
                ),
            )
        },
    )

@Preview(name = "First launch · phone", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun FirstLaunchPreview() = HomePreview(ready = false)

@Preview(name = "Saved audiogram · phone", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ReadyPreview() = HomePreview(ready = true)

@Preview(name = "Dark · phone", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun DarkPreview() = HomePreview(ready = true, dark = true)

@Preview(name = "Large text · phone", widthDp = 360, heightDp = 900, fontScale = 1.5f)
@Composable
private fun LargeTextPreview() = HomePreview(ready = true)

@Preview(name = "Tablet · overview", widthDp = 1000, heightDp = 900)
@Composable
private fun TabletPreview() = HomePreview(ready = true)

@Preview(name = "Listening control", widthDp = 390, heightDp = 700)
@Composable
private fun ListeningPreview() {
    SunoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            EarPage {
                AmplificationCard(ListenState(hasProfile = true), {}, {})
            }
        }
    }
}

@Composable
private fun HomePreview(
    ready: Boolean,
    dark: Boolean = false,
) {
    SunoTheme(darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                    MediaScreen(
                        state = AppState(hasProfile = ready, audiogram = if (ready) previewAudiogram() else null),
                        onOpenProfile = {},
                        onSetMediaEq = {},
                        onSetMediaBoost = {},
                        onSetMediaProcessingMode = {},
                    )
                }
                navigationBarContent(PrimaryDestination.ASSIST) {}
            }
        }
    }
}

@Preview(name = "Consent · phone", widthDp = 390, heightDp = 844)
@Preview(name = "Consent · large text", widthDp = 360, heightDp = 800, fontScale = 1.5f)
@Composable
private fun ConsentPreview() {
    SunoTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) { OnboardingScreen {} }
    }
}

@Preview(name = "Consent · dark", widthDp = 390, heightDp = 844)
@Composable
private fun DarkConsentPreview() {
    SunoTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) { OnboardingScreen {} }
    }
}

@Preview(name = "Profiles", widthDp = 390, heightDp = 844)
@Preview(name = "Profiles · large text", widthDp = 360, heightDp = 800, fontScale = 1.5f)
@Preview(name = "Profiles · tablet", widthDp = 840, heightDp = 900)
@Composable
private fun ProfilesPreview() {
    SunoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ProfileSetupScreen(AppState(hasProfile = true, audiogram = previewAudiogram()), {}, {}, {})
        }
    }
}
