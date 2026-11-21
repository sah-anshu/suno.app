// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.ui.patient

import au.remotiq.suno.phone.CallRoute

/** Actions from the call screens. */
class CallCallbacks(
    val onAnswer: () -> Unit = {},
    val onDecline: () -> Unit = {},
    val onEnd: () -> Unit = {},
    val onMute: (Boolean) -> Unit = {},
    val onRoute: (CallRoute) -> Unit = {},
    val onVolume: (Boolean) -> Unit = {},
)
