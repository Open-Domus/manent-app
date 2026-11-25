// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.transcribe

import it.manentlabs.app.speech.SpokenAnswers
import it.manentlabs.app.speech.spokenReply
import it.manentlabs.app.voice.CommandOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SpokenAndWavTest {
    @Test
    fun `a wav has the 44-byte header for 16 kHz mono 16-bit`() {
        val wav = Wav.of(shortArrayOf(1, -2, 300))
        assertEquals(44 + 6, wav.size)
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals(36 + 6, b.getInt(4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertEquals("fmt ", String(wav, 12, 4))
        assertEquals(16, b.getInt(16))
        assertEquals(1.toShort(), b.getShort(20))
        assertEquals(1.toShort(), b.getShort(22))
        assertEquals(16_000, b.getInt(24))
        assertEquals(32_000, b.getInt(28))
        assertEquals(2.toShort(), b.getShort(32))
        assertEquals(16.toShort(), b.getShort(34))
        assertEquals("data", String(wav, 36, 4))
        assertEquals(6, b.getInt(40))
        assertEquals((-2).toShort(), b.getShort(46))
    }

    @Test
    fun `which replies are spoken`() {
        val answer = CommandOutcome.Done("It is 20 degrees", answered = true)
        val confirmation = CommandOutcome.Done("Turned on the light")
        assertNull(spokenReply(answer, SpokenAnswers.OFF))
        assertEquals("It is 20 degrees", spokenReply(answer, SpokenAnswers.QUESTIONS))
        assertNull(spokenReply(confirmation, SpokenAnswers.QUESTIONS))
        assertEquals("Turned on the light", spokenReply(confirmation, SpokenAnswers.ALWAYS))
        assertNull(spokenReply(CommandOutcome.Done("  ", answered = true), SpokenAnswers.ALWAYS))
        assertNull(spokenReply(CommandOutcome.Done(), SpokenAnswers.ALWAYS))
        assertNull(spokenReply(CommandOutcome.NotUnderstood("Sorry"), SpokenAnswers.ALWAYS))
        assertNull(spokenReply(CommandOutcome.Failed("x"), SpokenAnswers.ALWAYS))
    }
}
