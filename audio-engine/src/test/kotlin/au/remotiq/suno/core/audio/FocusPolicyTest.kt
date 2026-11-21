// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.core.audio

import android.media.AudioManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FocusPolicyTest {
    @Test
    fun `a notification sound does not interrupt listening`() {
        assertEquals(FocusAction.CONTINUE, FocusPolicy.actionFor(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK))
    }

    @Test
    fun `a short interruption pauses instead of ending the session`() {
        assertEquals(FocusAction.PAUSE, FocusPolicy.actionFor(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))
        assertEquals(FocusAction.RESUME, FocusPolicy.actionFor(AudioManager.AUDIOFOCUS_GAIN))
    }

    @Test
    fun `a permanent loss ends the session`() {
        assertEquals(FocusAction.STOP, FocusPolicy.actionFor(AudioManager.AUDIOFOCUS_LOSS))
    }
}
