package au.remotiq.suno.data

import kotlinx.coroutines.flow.Flow

interface PreferenceStorage {
    /** Companion app: this phone's random link id, created once. */
    fun observeLinkClientId(): Flow<String?>

    suspend fun setLinkClientId(id: String)

    /** The patient's join code (see the link module), created once; null until first needed. */
    fun observeLinkIdentity(): Flow<String?>

    suspend fun setLinkIdentity(joinCodeUri: String)

    /** Companions the patient's phone has allowed. They rejoin without asking again. */
    fun observeApprovedCompanions(): Flow<List<StoredCompanion>>

    suspend fun setApprovedCompanions(companions: List<StoredCompanion>)

    /** Settings for the patient's simple layout and the helper PIN. */
    fun observePatientSettings(): Flow<PatientSettings>

    suspend fun setPatientSettings(settings: PatientSettings)

    /** People, call screening and auto-answer for the patient's phone calls (helper setting). */
    fun observeCallSettings(): Flow<CallSettings>

    suspend fun setCallSettings(settings: CallSettings)

    /** What the patient's SOS does (helper setting). */
    fun observeSosSettings(): Flow<SosSettings>

    suspend fun setSosSettings(settings: SosSettings)

    /** True only when the user has explicitly accepted the current terms and safety guidance. */
    fun observeConsentAccepted(): Flow<Boolean>

    suspend fun setConsentAccepted(accepted: Boolean)

    /** Whether the optional background-listening setup has been handled, not an OS permission grant. */
    fun observeBackgroundSetupReviewed(): Flow<Boolean>

    suspend fun markBackgroundSetupReviewed()

    fun observeHighContrast(): Flow<Boolean>

    suspend fun setHighContrast(enabled: Boolean)

    fun observeListeningFeatures(): Flow<ListeningFeatures>

    /** Removing media sound also clears its playback toggle. */
    suspend fun setListeningFeatures(features: ListeningFeatures)

    /** Linear digital ceiling in (0, 1]; use a conservative default until comfort setup. */
    fun observeComfortCeiling(): Flow<Float>

    suspend fun setComfortCeiling(value: Float)

    /** Stored preset name; unknown values resolve to Standard in the app layer. */
    fun observeAssistPreset(): Flow<String>

    suspend fun setAssistPreset(name: String)

    /** Saved microphone choice; app maps unknown names to the phone microphone. */
    fun observeMicrophoneSource(): Flow<String>

    suspend fun setMicrophoneSource(name: String)

    fun observeListeningSettings(): Flow<SoundPreferences>

    suspend fun setListeningSettings(settings: SoundPreferences)

    fun observeMediaEqEnabled(): Flow<Boolean>

    suspend fun setMediaEqEnabled(enabled: Boolean)

    /** Independent media boost in dB; does not change any assist profile. */
    fun observeMediaBoostDb(): Flow<Float>

    suspend fun setMediaBoostDb(db: Float)

    /** Media dynamics algorithm name; mapped to the DSP enum by the app layer. */
    fun observeMediaProcessingMode(): Flow<String>

    suspend fun setMediaProcessingMode(name: String)

    /** Relative exposure units, never dB SPL; resets when the epoch day changes. */
    fun observeExposureToday(): Flow<DailyListening>

    /** Add [units] to [epochDay]; a different stored day is replaced, not summed. */
    suspend fun addExposureUnits(
        units: Double,
        epochDay: Long,
    )
}

/** Features available in Listen; choosing both does not start either feature. */
enum class ListeningFeatures(
    val liveListeningEnabled: Boolean,
    val mediaSoundEnabled: Boolean,
) {
    BOTH(true, true),
    LIVE_ONLY(true, false),
    MEDIA_ONLY(false, true),
    ;

    companion object {
        fun fromName(name: String?): ListeningFeatures = entries.firstOrNull { it.name == name } ?: BOTH
    }
}

/** Exposure units accumulated on [epochDay] (may be a past day until next write). */
data class DailyListening(
    val epochDay: Long,
    val units: Double,
)

/** Names are mapped to DSP enums by the app, keeping persistence independent of audio. */
data class SoundPreferences(
    val noiseReduction: String = "OFF",
    val voiceComfort: String = "GENTLE",
    val captureMode: String = "NATURAL",
    val speechClarity: String = "GENTLE",
    val quietSpeech: String = "OFF",
    val speechEngine: String = "RNNOISE",
)
