// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.ui.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.remotiq.suno.data.PreferenceStorage
import au.remotiq.suno.data.StoredCompanion
import au.remotiq.suno.family.HostStatus
import au.remotiq.suno.family.PatientLinkHost
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State for the helper's family-link settings. */
@HiltViewModel
class FamilyHelperModel
    @Inject
    constructor(
        private val preferences: PreferenceStorage,
        private val link: PatientLinkHost,
    ) : ViewModel() {
        val approved: StateFlow<List<StoredCompanion>> =
            preferences.observeApprovedCompanions().stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                emptyList(),
            )
        val name: StateFlow<String> =
            preferences
                .observePatientSettings()
                .map {
                    it.displayName
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
        val status: StateFlow<HostStatus> = link.status

        fun setName(value: String) {
            viewModelScope.launch {
                preferences.setPatientSettings(preferences.observePatientSettings().first().copy(displayName = value))
            }
        }

        suspend fun joinUri(): String = link.joinCode().toUri()

        fun hold(on: Boolean) = if (on) link.acquire(HOLDER) else link.release(HOLDER)

        fun remove(id: String) = link.remove(id)

        private companion object {
            const val HOLDER = "join-code"
        }
    }
