package au.remotiq.suno.assist

import au.remotiq.suno.core.audio.speech.BassReduction
import au.remotiq.suno.core.audio.speech.EnhancementEngine
import au.remotiq.suno.core.audio.speech.EnhancementOptions
import au.remotiq.suno.core.audio.speech.InputMode
import au.remotiq.suno.core.audio.speech.NoiseStrength
import au.remotiq.suno.core.audio.speech.SpeechPresence
import au.remotiq.suno.core.audio.speech.VoiceBoost
import au.remotiq.suno.data.SoundPreferences

fun SoundPreferences.toOptions(): EnhancementOptions =
    EnhancementOptions(
        noiseReduction = NoiseStrength.fromName(noiseReduction),
        voiceComfort = BassReduction.fromName(voiceComfort),
        captureMode = InputMode.fromName(captureMode),
        speechClarity = SpeechPresence.fromName(speechClarity),
        quietSpeech = VoiceBoost.fromName(quietSpeech),
        speechEngine = EnhancementEngine.fromName(speechEngine),
    )

fun EnhancementOptions.toSettings(): SoundPreferences =
    SoundPreferences(
        noiseReduction = noiseReduction.name,
        voiceComfort = voiceComfort.name,
        captureMode = captureMode.name,
        speechClarity = speechClarity.name,
        quietSpeech = quietSpeech.name,
        speechEngine = speechEngine.name,
    )
