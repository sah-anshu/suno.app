// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.phone

import android.telecom.Call
import android.telecom.CallScreeningService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * With "calls from contacts only", declines unknown callers before the phone rings. They still appear
 * in the call log. Every call rings during an SOS.
 */
@AndroidEntryPoint
class SunoCallScreeningService : CallScreeningService() {
    @Inject
    lateinit var calls: PhoneCalls

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onScreenCall(details: Call.Details) {
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }
        scope.launch {
            val number = details.handle?.schemeSpecificPart.orEmpty()
            val block = withTimeoutOrNull(DECIDE_MS) { calls.shouldBlock(number) } ?: false
            val response =
                if (block) {
                    CallResponse
                        .Builder()
                        .setDisallowCall(true)
                        .setRejectCall(true)
                        .setSkipNotification(true)
                        .build()
                } else {
                    CallResponse.Builder().build()
                }
            respondToCall(details, response)
        }
    }

    private companion object {
        /** Android waits about five seconds for an answer. */
        const val DECIDE_MS = 3_000L
    }
}
