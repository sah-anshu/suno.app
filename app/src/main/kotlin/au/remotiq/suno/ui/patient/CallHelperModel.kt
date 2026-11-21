// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.ui.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.remotiq.suno.data.CallSettings
import au.remotiq.suno.data.Favourite
import au.remotiq.suno.data.PreferenceStorage
import au.remotiq.suno.phone.PhoneCalls
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Helper mode → Settings → People and calls. */
@HiltViewModel
class CallHelperModel
    @Inject
    constructor(
        private val preferences: PreferenceStorage,
        private val calls: PhoneCalls,
    ) : ViewModel() {
        val settings: StateFlow<CallSettings> =
            preferences.observeCallSettings().stateIn(viewModelScope, SharingStarted.Eagerly, CallSettings())

        fun isPhoneApp(): Boolean = calls.isDefaultPhoneApp()

        fun add(person: Favourite) = update { it.copy(favourites = it.favourites + person) }

        fun remove(index: Int) = update { it.copy(favourites = it.favourites.filterIndexed { i, _ -> i != index }) }

        /** Moves a person up (-1) or down (+1) in People. */
        fun move(
            index: Int,
            by: Int,
        ) = update {
            val list = it.favourites.toMutableList()
            val target = index + by
            if (index in list.indices && target in list.indices) {
                val person = list.removeAt(index)
                list.add(target, person)
            }
            it.copy(favourites = list)
        }

        fun set(transform: (CallSettings) -> CallSettings) = update(transform)

        private fun update(transform: (CallSettings) -> CallSettings) {
            viewModelScope.launch { preferences.setCallSettings(transform(preferences.observeCallSettings().first())) }
        }
    }
