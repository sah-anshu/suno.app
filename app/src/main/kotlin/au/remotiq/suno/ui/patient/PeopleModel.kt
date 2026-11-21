// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.ui.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.remotiq.suno.data.Favourite
import au.remotiq.suno.data.PreferenceStorage
import au.remotiq.suno.phone.PhoneCalls
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** People, the keypad and the Home "Call" button. */
@HiltViewModel
class PeopleModel
    @Inject
    constructor(
        preferences: PreferenceStorage,
        private val calls: PhoneCalls,
    ) : ViewModel() {
        val people: StateFlow<List<Favourite>> =
            preferences
                .observeCallSettings()
                .map { it.favourites }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), emptyList())

        /** Who the Home "Call" button calls: the first person in People, else the SOS helper. */
        val quickCall: StateFlow<Favourite?> =
            combine(preferences.observeCallSettings(), preferences.observeSosSettings()) { c, s ->
                c.favourites.firstOrNull()
                    ?: Favourite(s.helperName, "", s.helperNumber).takeIf { s.hasHelper && s.helperName.isNotBlank() }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

        fun call(number: String) = calls.placeCall(number)

        private companion object {
            const val STOP_MS = 5_000L
        }
    }
