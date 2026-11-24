// SPDX-License-Identifier: MIT

package it.manentlabs.app.speech

import dev.omnesis.android.feature.assist.AssistOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpokenReplyTest {
    private val answer = AssistOutcome.Done("The boiler is at 52 degrees", answered = true)
    private val confirmation = AssistOutcome.Done("Turned on the light")

    @Test fun anAnswerIsSpokenUnlessOff() {
        assertEquals("The boiler is at 52 degrees", spokenReply(answer, SpokenAnswers.QUESTIONS))
        assertEquals("The boiler is at 52 degrees", spokenReply(answer, SpokenAnswers.ALWAYS))
        assertNull(spokenReply(answer, SpokenAnswers.OFF))
    }

    @Test fun aConfirmationIsSpokenOnlyWhenEveryReplyIs() {
        assertNull(spokenReply(confirmation, SpokenAnswers.QUESTIONS))
        assertEquals("Turned on the light", spokenReply(confirmation, SpokenAnswers.ALWAYS))
    }

    @Test fun nothingToSayStaysQuiet() {
        assertNull(spokenReply(AssistOutcome.Done(null, answered = true), SpokenAnswers.ALWAYS))
        assertNull(spokenReply(AssistOutcome.Done("  ", answered = true), SpokenAnswers.ALWAYS))
        assertNull(spokenReply(AssistOutcome.NotUnderstood("no_intent_match"), SpokenAnswers.ALWAYS))
        assertNull(spokenReply(AssistOutcome.Failed("timeout"), SpokenAnswers.ALWAYS))
    }
}
