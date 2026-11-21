// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.di

import au.remotiq.suno.family.CompanionLink
import au.remotiq.suno.family.ShareSink
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The companion app shares sound over its link to the patient. */
@Module
@InstallIn(SingletonComponent::class)
object ShareBindings {
    @Provides
    fun shareSink(link: CompanionLink): ShareSink = link
}
