// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.ui.patient

import androidx.lifecycle.ViewModel
import au.remotiq.suno.sos.SosController
import au.remotiq.suno.sos.SosState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SosModel
    @Inject
    constructor(
        private val controller: SosController,
    ) : ViewModel() {
        val state: StateFlow<SosState> = controller.state

        fun ask() = controller.ask()

        val callbacks =
            SosCallbacks(
                onYes = controller::confirm,
                onNo = controller::cancel,
                onCallHelper = controller::callHelper,
                onCallEmergency = controller::callEmergency,
                onSendTexts = controller::openTexts,
                onAlarm = controller::setAlarm,
                onSafe = controller::end,
            )
    }
