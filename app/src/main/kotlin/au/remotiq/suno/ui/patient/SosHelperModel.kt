// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.ui.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.remotiq.suno.data.PreferenceStorage
import au.remotiq.suno.data.SosSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The helper's SOS settings screen. */
@HiltViewModel
class SosHelperModel
    @Inject
    constructor(
        private val preferences: PreferenceStorage,
    ) : ViewModel() {
        val settings: StateFlow<SosSettings?> =
            preferences.observeSosSettings().stateIn(viewModelScope, SharingStarted.Eagerly, null)

        fun save(settings: SosSettings) {
            viewModelScope.launch { preferences.setSosSettings(settings) }
        }
    }
