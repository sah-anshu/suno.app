// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.core.audio

import android.media.AudioManager

/** What a live listening session does when Android changes audio focus. */
enum class FocusAction {
    /** Keep listening; another sound briefly plays alongside (for example a notification). */
    CONTINUE,

    /** Fade out and keep the session open; another app needs the output for a short time. */
    PAUSE,

    /** Fade back in after a [PAUSE]. */
    RESUME,

    /** Another app took the output for good; end the session quietly. */
    STOP,
}

/**
 * Maps audio-focus changes to session actions. Short interruptions must not end a
 * hearing-assist session, because a patient may not be able to restart it.
 */
object FocusPolicy {
    fun actionFor(change: Int): FocusAction =
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
            -> FocusAction.RESUME
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> FocusAction.CONTINUE
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> FocusAction.PAUSE
            else -> FocusAction.STOP
        }
}
