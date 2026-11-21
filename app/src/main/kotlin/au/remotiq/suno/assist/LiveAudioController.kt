package au.remotiq.suno.assist

import android.content.Context
import android.media.AudioManager
import au.remotiq.suno.audiogram.FrequencyGainCurve
import au.remotiq.suno.core.audio.AndroidStreamEngine
import au.remotiq.suno.core.audio.InputSource
import au.remotiq.suno.core.audio.StreamPhase
import au.remotiq.suno.core.audio.StreamSpec
import au.remotiq.suno.core.audio.StreamStatus
import au.remotiq.suno.core.audio.dsp.MeterWindow
import au.remotiq.suno.core.audio.dsp.MeteredTransform
import au.remotiq.suno.core.audio.dsp.MonoListeningChain
import au.remotiq.suno.core.audio.dsp.SignalMeter
import au.remotiq.suno.core.audio.dsp.StereoListeningChain
import au.remotiq.suno.core.audio.speech.DpdfnetBridge
import au.remotiq.suno.core.audio.speech.EnhancementEngine
import au.remotiq.suno.core.audio.speech.EnhancementOptions
import au.remotiq.suno.core.audio.speech.FrameFilter
import au.remotiq.suno.core.audio.speech.InputEnhancement
import au.remotiq.suno.core.audio.speech.NoiseStrength
import au.remotiq.suno.core.audio.speech.RnnoiseBridge
import au.remotiq.suno.core.audio.speech.SpeexBridge
import au.remotiq.suno.core.audio.speech.WienerBridge
import au.remotiq.suno.mediaeq.MediaSoundController
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live relative-exposure numbers for the current session, published by
 * [LiveAudioService]'s sampling loop. `unflushedUnits` is the portion not yet
 * persisted (the UI adds it to the stored day so the meter never jumps).
 */
data class ListeningSnapshot(
    val sessionSeconds: Long = 0,
    val sessionUnits: Double = 0.0,
    val unflushedUnits: Double = 0.0,
    val lastRmsDbfs: Double = Double.NEGATIVE_INFINITY,
)

/** Immutable configuration for an assist session, derived from the active profile. */
data class LiveAudioConfig(
    /** Per-ear fitted curves; left ear drives the left channel, right the right. */
    val leftGainCurve: FrequencyGainCurve,
    val rightGainCurve: FrequencyGainCurve,
    val masterGainDb: Double,
    /** Output ceiling (linear) from comfort calibration; the limiter enforces it. */
    val ceilingLinear: Float = MonoListeningChain.DEFAULT_CEILING_LINEAR,
    /** Optional low-cut from the environment preset (e.g. outdoors wind cut). */
    val highPassHz: Double? = null,
    val sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
    val framesPerBlock: Int = DEFAULT_FRAMES_PER_BLOCK,
    val listeningOptions: EnhancementOptions = EnhancementOptions(),
    val microphoneSource: InputSource = InputSource.PHONE,
    val requireHeadphones: Boolean = false,
) {
    companion object {
        const val DEFAULT_SAMPLE_RATE_HZ = 48_000

        // ~4 ms at 48 kHz — a low-latency block size; the engine adjusts device buffers.
        const val DEFAULT_FRAMES_PER_BLOCK = 192
    }
}

/**
 * Single source of truth for Hearing Assist state, shared between the UI and the
 * foreground [LiveAudioService]. The UI sets [config] and starts/stops the service;
 * the service drives the engine through here. Mute is immediate.
 */
@Singleton
class LiveAudioController
    @Inject
    constructor(
        @ApplicationContext context: Context,
        mediaEq: MediaSoundController,
    ) {
        private val applicationContext = context.applicationContext
        private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        private val _sessionStatus = MutableStateFlow(StreamStatus())
        val sessionStatus: StateFlow<StreamStatus> = _sessionStatus.asStateFlow()

        private val engine =
            AndroidStreamEngine(context, bypassEffects = mediaEq::bypassForPlayback) { status ->
                _sessionStatus.value = status
                when (status.state) {
                    StreamPhase.RUNNING -> _running.value = true
                    StreamPhase.CONNECTING -> Unit
                    StreamPhase.STOPPED,
                    StreamPhase.FAILED,
                    -> {
                        _running.value = false
                    }
                }
            }

        @Volatile
        private var config: LiveAudioConfig? = null

        @Volatile
        private var chain: StereoListeningChain? = null

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        // Post-limiter output tap; drained ~1/s by the service's sampling loop.
        private val outputMeter = SignalMeter()

        private val _exposure = MutableStateFlow(ListeningSnapshot())
        val exposure: StateFlow<ListeningSnapshot> = _exposure.asStateFlow()

        /** Drain the level window accumulated since the last call (service loop). */
        fun drainOutputLevel(): MeterWindow = outputMeter.drain()

        fun publishExposure(snapshot: ListeningSnapshot) {
            _exposure.value = snapshot
        }

        fun configure(newConfig: LiveAudioConfig) {
            config = newConfig
        }

        fun hasConfig(): Boolean = config != null

        /**
         * Adjusts master gain immediately, including while the engine is running.
         * The chain clamps to the safety cap and the limiter stays downstream.
         */
        fun setMasterGainDb(db: Double) {
            config = config?.copy(masterGainDb = db)
            chain?.setMasterGainDb(db)
        }

        /** Build the per-ear chains from the current config and start the real-time loop. */
        fun startEngine() {
            val c = config ?: return
            if (engine.isRunning || _sessionStatus.value.state == StreamPhase.CONNECTING) return
            outputMeter.drain() // discard anything left from a previous session
            engine.startSession(
                StreamSpec(
                    c.sampleRateHz,
                    channelCount = 2,
                    framesPerBlock = c.framesPerBlock,
                    inputTuning =
                        c.listeningOptions.captureMode.inputTuning(
                            manager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true",
                        ),
                    microphoneSource = c.microphoneSource,
                    requireHeadphones = c.requireHeadphones,
                ),
            ) { actual ->
                val current = config ?: c
                val left =
                    MonoListeningChain(
                        gainCurve = current.leftGainCurve,
                        sampleRateHz = actual.sampleRateHz,
                        masterGainDb = current.masterGainDb,
                        ceilingLinear = current.ceilingLinear,
                        highPassHz = current.highPassHz,
                        feedbackGuardEnabled = true,
                        speechPresenceDb = c.listeningOptions.speechClarity.gainDb,
                    )
                val right =
                    MonoListeningChain(
                        gainCurve = current.rightGainCurve,
                        sampleRateHz = actual.sampleRateHz,
                        masterGainDb = current.masterGainDb,
                        ceilingLinear = current.ceilingLinear,
                        highPassHz = current.highPassHz,
                        feedbackGuardEnabled = true,
                        speechPresenceDb = c.listeningOptions.speechClarity.gainDb,
                    )
                val newChain = StereoListeningChain(left, right, actual.framesPerBlock)
                chain = newChain
                // The meter taps the buffer AFTER the chain (post-limiter), so it
                // sees exactly what reaches the device; the chain stays untouched.
                val denoiser = createDenoiser(actual.sampleRateHz, c.listeningOptions)
                InputEnhancement(
                    actual.sampleRateHz,
                    c.listeningOptions,
                    MeteredTransform(newChain, outputMeter),
                    denoiser,
                )
            }
        }

        private fun createDenoiser(
            rate: Int,
            options: EnhancementOptions,
        ): FrameFilter? {
            if (options.noiseReduction == NoiseStrength.OFF) return null
            val suppression = options.noiseReduction.suppressionDb
            return when (options.speechEngine) {
                EnhancementEngine.RNNOISE -> RnnoiseBridge(rate, suppression, options.quietSpeech.maximumGainDb)
                EnhancementEngine.DPDFNET -> DpdfnetBridge(applicationContext, rate, suppression)
                EnhancementEngine.SPEEX -> SpeexBridge(rate, suppression)
                EnhancementEngine.WIENER -> WienerBridge(rate, suppression)
            }
        }

        fun stopEngine() {
            engine.stop()
            chain = null
            _running.value = false
            _exposure.value = ListeningSnapshot()
        }
    }
