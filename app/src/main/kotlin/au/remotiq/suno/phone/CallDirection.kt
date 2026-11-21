// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.phone

import android.os.Build
import android.telecom.Call

/** True for a call someone made to the patient. Before Android 10 a new incoming call is ringing. */
internal val Call.isIncoming: Boolean
    get() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            details.callDirection == Call.Details.DIRECTION_INCOMING
        } else {
            @Suppress("DEPRECATION")
            state == Call.STATE_RINGING
        }

/** Screening details: before Android 10 only incoming calls are screened. */
internal val Call.Details.isIncoming: Boolean
    get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || callDirection == Call.Details.DIRECTION_INCOMING
