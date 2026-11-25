// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

import it.manentlabs.app.audio.VoiceDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTest {
    /** Scores frames in call order from a script, ignoring the audio; calls past the script score [rest]. */
    private class Scripted(private val script: List<Float>, private val rest: Float = 0f) : VoiceDetector {
        var index = 0
        var resets = 0
        override fun score(frame: ShortArray): Float = script.getOrElse(index++) { rest }
        override fun reset() {
            resets++
        }
        override fun close() = Unit
    }

    private fun segments(vararg p: Float): Boolean {
        val s = SpeechSegments()
        p.forEach { s.onFrame(it) }
        return s.found
    }

    private fun repeat(p: Float, n: Int) = FloatArray(n) { p }

    @Test
    fun `a segment needs 250 ms to count`() {
        assertFalse(segments(*repeat(0.9f, 7)))
        assertTrue(segments(*repeat(0.9f, 8)))
    }

    @Test
    fun `a segment starts at 0_5 and carries through 0_35`() {
        assertFalse(segments(*repeat(0.45f, 20)))
        assertTrue(segments(0.6f, *repeat(0.4f, 7)))
    }

    @Test
    fun `100 ms of quiet ends a segment`() {
        assertFalse(segments(*repeat(0.9f, 4), *repeat(0.1f, 4), *repeat(0.9f, 4)))
        assertTrue(segments(*repeat(0.9f, 4), *repeat(0.1f, 3), *repeat(0.9f, 4)))
    }

    private fun pcm(seconds: Double) = ShortArray((seconds * 16_000).toInt())

    @Test
    fun `no speech`() {
        assertEquals(SpeechVerdict.NoSpeech, SpeechScan.scan(pcm(5.0), Scripted(emptyList())))
    }

    @Test
    fun `speech early only is not speech at the end`() {
        val detector = Scripted(List(10) { 0.9f })
        assertEquals(SpeechVerdict.Speech(atEnd = false), SpeechScan.scan(pcm(5.0), detector))
        assertEquals(2, detector.resets)
    }

    @Test
    fun `speech in the last two seconds is speech at the end`() {
        assertEquals(SpeechVerdict.Speech(atEnd = true), SpeechScan.scan(pcm(5.0), Scripted(List(10) { 0.9f }, rest = 0.9f)))
    }

    @Test
    fun `a failing detector gives unknown`() {
        val broken = object : VoiceDetector {
            override fun score(frame: ShortArray): Float = throw IllegalStateException("model")
            override fun reset() = Unit
            override fun close() = Unit
        }
        assertEquals(SpeechVerdict.Unknown, SpeechScan.scan(pcm(1.0), broken))
    }

    @Test
    fun `filing keeps speech and drops silence`() {
        val speech = FilingPolicy.decide(true, 60_000, SpeechVerdict.Speech(atEnd = true), false)
        assertFalse(speech.silent)
        assertTrue(speech.endsInSpeech)
        assertTrue(speech.heldSpeech)

        val quiet = FilingPolicy.decide(true, 60_000, SpeechVerdict.NoSpeech, false)
        assertTrue(quiet.silent)
        assertFalse(quiet.heldSpeech)
    }

    @Test
    fun `the part after one ending in speech keeps its audio`() {
        val after = FilingPolicy.decide(true, 60_000, SpeechVerdict.NoSpeech, previousEndedInSpeech = true)
        assertFalse(after.silent)
        assertFalse(after.endsInSpeech)
    }

    @Test
    fun `unknown is kept as speech running to the end`() {
        val unknown = FilingPolicy.decide(true, 60_000, SpeechVerdict.Unknown, false)
        assertFalse(unknown.silent)
        assertTrue(unknown.endsInSpeech)
    }

    @Test
    fun `empty or zero-length parts are silent`() {
        assertTrue(FilingPolicy.decide(false, 2_000, SpeechVerdict.NoSpeech, true).silent)
        assertTrue(FilingPolicy.decide(true, 0, SpeechVerdict.Speech(true), true).silent)
    }
}
