// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.di

import au.remotiq.suno.data.PreferenceStorage
import au.remotiq.suno.family.PatientLinkHost
import au.remotiq.suno.sos.AndroidSosActions
import au.remotiq.suno.sos.SosController
import au.remotiq.suno.sos.SosStart
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SosBindings {
    @Provides
    @Singleton
    fun sosController(
        preferences: PreferenceStorage,
        actions: AndroidSosActions,
        link: PatientLinkHost,
    ): SosController =
        SosController(
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            {
                SosStart(
                    settings = preferences.observeSosSettings().first(),
                    patientName = preferences.observePatientSettings().first().displayName,
                    emergencyIfNoAnswer = preferences.observeCallSettings().first().emergencyIfNoAnswer,
                )
            },
            actions,
            link,
        )
}
