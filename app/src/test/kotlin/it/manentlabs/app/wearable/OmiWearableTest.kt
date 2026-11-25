// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable

import it.manentlabs.app.feature.assist.CommandFeedback
import it.manentlabs.app.feature.omi.Haptic
import org.junit.Assert.assertEquals
import org.junit.Test

class OmiWearableTest {
    @Test fun everyFeedbackHasItsOwnPattern() {
        assertEquals(Haptic.STARTED, OmiWearable.haptic(CommandFeedback.LISTENING))
        assertEquals(Haptic.STOPPED, OmiWearable.haptic(CommandFeedback.DONE))
        assertEquals(Haptic.FAILED, OmiWearable.haptic(CommandFeedback.FAILED))
        assertEquals(CommandFeedback.entries.size, CommandFeedback.entries.map(OmiWearable::haptic).toSet().size)
    }
}
