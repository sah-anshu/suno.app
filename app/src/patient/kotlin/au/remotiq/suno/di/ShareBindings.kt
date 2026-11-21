// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.di

import au.remotiq.suno.family.ShareSink
import au.remotiq.suno.link.android.ShareSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The patient app never shares sound; ShareService is not declared in its manifest. */
@Module
@InstallIn(SingletonComponent::class)
object ShareBindings {
    @Provides
    fun shareSink(): ShareSink =
        object : ShareSink {
            override val connected: StateFlow<Boolean> = MutableStateFlow(false)

            override fun sendMedia(frame: ShortArray) = Unit

            override fun onSharing(source: ShareSource?) = Unit
        }
}
