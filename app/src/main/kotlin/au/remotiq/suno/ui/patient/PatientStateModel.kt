// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.ui.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.remotiq.suno.assist.LiveAudioController
import au.remotiq.suno.assist.LiveSessionBuilder
import au.remotiq.suno.assist.toOptions
import au.remotiq.suno.assist.toSettings
import au.remotiq.suno.audiograph.SourceMode
import au.remotiq.suno.audiograph.Talker
import au.remotiq.suno.audiograph.VoiceMixer
import au.remotiq.suno.core.audio.StreamPhase
import au.remotiq.suno.data.PatientSettings
import au.remotiq.suno.data.PreferenceStorage
import au.remotiq.suno.family.PatientLinkHost
import au.remotiq.suno.link.CompanionDevice
import au.remotiq.suno.patient.PinCode
import au.remotiq.suno.patient.Place
import au.remotiq.suno.patient.VolumeSteps
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** What the Home screen needs to know about the device right now. */
data class DeviceStatus(
    val earphones: Boolean,
    val batteryPercent: Int?,
)

data class PatientUiState(
    val loaded: Boolean = false,
    val settings: PatientSettings = PatientSettings(),
    val running: Boolean = false,
    val connecting: Boolean = false,
    val failed: Boolean = false,
    val consentAccepted: Boolean = false,
    /** Phones waiting for the patient to allow them. */
    val joinRequests: List<CompanionDevice> = emptyList(),
    /** Connected family members (sources the patient can choose). */
    val companions: List<CompanionDevice> = emptyList(),
    /** People talking right now. */
    val talkers: List<Talker> = emptyList(),
    val sourceMode: SourceMode = SourceMode.AUTO,
) {
    val place: Place get() = Place.fromName(settings.place)
    val volumeStep: Int get() = VolumeSteps.effectiveStep(settings, place)
    val volumeCap: Int get() = minOf(settings.maxVolumeStep, place.maxVolumeStep ?: PatientSettings.VOLUME_STEPS)
    val theme: PatientThemeName get() = PatientThemeName.fromName(settings.theme)
    val hasPin: Boolean get() = settings.pinHash != null

    /** True when connecting earphones should start listening on its own. */
    val autoStartReady: Boolean get() = settings.autoStart && consentAccepted && !running && !connecting
}

/**
 * State for the patient's simple layout. Reuses EarCast's session pipeline ([LiveSessionBuilder]
 * and [LiveAudioController]); the screen starts and stops the foreground service because that
 * needs a Context and permissions.
 */
@HiltViewModel
class PatientStateModel
    @Inject
    constructor(
        private val controller: LiveAudioController,
        private val preferences: PreferenceStorage,
        private val sessionBuilder: LiveSessionBuilder,
        private val familyLink: PatientLinkHost,
        private val voiceMixer: VoiceMixer,
    ) : ViewModel() {
        private val sourceMode = MutableStateFlow(voiceMixer.mode)

        val uiState: StateFlow<PatientUiState> =
            combine(
                preferences.observePatientSettings(),
                controller.running,
                controller.sessionStatus,
                preferences.observeConsentAccepted(),
            ) { settings, running, status, consent ->
                PatientUiState(
                    loaded = true,
                    settings = settings,
                    running = running,
                    connecting = status.state == StreamPhase.CONNECTING,
                    failed = status.state == StreamPhase.FAILED,
                    consentAccepted = consent,
                )
            }.combine(
                combine(familyLink.pending, familyLink.connected, familyLink.talkers, sourceMode, ::FamilyState),
            ) { s, f ->
                s.copy(joinRequests = f.pending, companions = f.connected, talkers = f.talkers, sourceMode = f.mode)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PatientUiState())

        fun allow(clientId: String) = familyLink.allow(clientId)

        fun deny(clientId: String) = familyLink.deny(clientId)

        /** Tapping the selected source again returns to Auto (a person talking comes to the front). */
        fun chooseSource(mode: SourceMode) {
            val next = if (sourceMode.value == mode) SourceMode.AUTO else mode
            voiceMixer.mode = next
            sourceMode.value = next
        }

        private data class FamilyState(
            val pending: List<CompanionDevice>,
            val connected: List<CompanionDevice>,
            val talkers: List<Talker>,
            val mode: SourceMode,
        )

        /** Applies the place, prepares the session and sets the patient's volume. Returns true if ready. */
        suspend fun prepare(): Boolean {
            val settings = preferences.observePatientSettings().first()
            applyPlace(Place.fromName(settings.place))
            val ready = sessionBuilder.prepare()
            if (ready) {
                controller.setMasterGainDb(
                    VolumeSteps.gainDb(VolumeSteps.effectiveStep(settings, Place.fromName(settings.place))),
                )
            }
            return ready
        }

        fun changeVolume(delta: Int) {
            viewModelScope.launch {
                val s = preferences.observePatientSettings().first()
                val place = Place.fromName(s.place)
                val cap = minOf(s.maxVolumeStep, place.maxVolumeStep ?: PatientSettings.VOLUME_STEPS)
                val step = (VolumeSteps.effectiveStep(s, place) + delta).coerceIn(1, cap)
                preferences.setPatientSettings(s.copy(volumeStep = step))
                controller.setMasterGainDb(VolumeSteps.gainDb(step))
            }
        }

        /** Saves the place. Returns true when a running session must restart to apply it. */
        suspend fun choosePlace(place: Place): Boolean {
            val s = preferences.observePatientSettings().first()
            preferences.setPatientSettings(s.copy(place = place.name))
            applyPlace(place)
            return controller.running.value
        }

        /** Waits until the session has stopped, up to a short timeout. */
        suspend fun awaitStopped() {
            withTimeoutOrNull(STOP_WAIT_MS) { controller.running.first { !it } }
        }

        fun updateSettings(transform: (PatientSettings) -> PatientSettings) {
            viewModelScope.launch {
                preferences.setPatientSettings(transform(preferences.observePatientSettings().first()))
            }
        }

        fun checkPin(pin: String): Boolean = PinCode.matches(pin, uiState.value.settings.pinHash)

        fun setPin(pin: String) {
            if (PinCode.isValid(pin)) updateSettings { it.copy(pinHash = PinCode.hash(pin)) }
        }

        private suspend fun applyPlace(place: Place) {
            val current = preferences.observeListeningSettings().first().toOptions()
            preferences.setListeningSettings(place.applyTo(current).toSettings())
            preferences.setAssistPreset(place.preset.name)
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
            const val STOP_WAIT_MS = 2_000L
        }
    }
