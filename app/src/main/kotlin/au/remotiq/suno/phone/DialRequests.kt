// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.phone

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A number another app asked to dial (tel: links, the system dial action). Opens Suno's keypad. */
object DialRequests {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    /** Returns true when [intent] was a dial request. */
    fun handle(intent: Intent?): Boolean {
        val action = intent?.action ?: return false
        if (action != Intent.ACTION_DIAL && action != Intent.ACTION_VIEW) return false
        val data = intent.data
        if (action == Intent.ACTION_VIEW && data?.scheme != "tel") return false
        _pending.value = data?.schemeSpecificPart.orEmpty()
        return true
    }

    fun consume() {
        _pending.value = null
    }
}
