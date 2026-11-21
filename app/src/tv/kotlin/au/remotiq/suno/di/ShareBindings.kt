// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.di

import au.remotiq.suno.family.ShareSink
import au.remotiq.suno.tv.TvLink
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Suno TV shares what the TV plays over its link to the patient. */
@Module
@InstallIn(SingletonComponent::class)
object ShareBindings {
    @Provides
    fun shareSink(link: TvLink): ShareSink = link
}
