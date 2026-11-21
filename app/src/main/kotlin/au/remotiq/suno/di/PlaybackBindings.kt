package au.remotiq.suno.di

import au.remotiq.suno.audiogram.ProfileFitting
import au.remotiq.suno.audiogram.halfGainFitting
import au.remotiq.suno.core.audio.PreviewPlayer
import au.remotiq.suno.core.audio.TestSignalGenerator
import au.remotiq.suno.core.audio.TestSignalPlayer
import au.remotiq.suno.mediaeq.MediaSoundController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides the audio + fitting collaborators used by the hearing-test screen. */
@Module
@InstallIn(SingletonComponent::class)
object PlaybackBindings {
    @Provides
    @Singleton
    fun toneGenerator(): TestSignalGenerator = TestSignalGenerator()

    // Not a singleton: each consumer gets its own AudioTrack-backed player and is
    // responsible for releasing it.
    @Provides
    fun tonePlayer(mediaEq: MediaSoundController): TestSignalPlayer =
        TestSignalPlayer(
            bypassEffects = mediaEq::bypassForPlayback,
        )

    // Same lifecycle contract as TestSignalPlayer: per-consumer, released by its owner.
    @Provides
    fun abPlayer(mediaEq: MediaSoundController): PreviewPlayer =
        PreviewPlayer(
            bypassEffects = mediaEq::bypassForPlayback,
        )

    @Provides
    @Singleton
    fun fittingStrategy(): ProfileFitting = halfGainFitting()
}
