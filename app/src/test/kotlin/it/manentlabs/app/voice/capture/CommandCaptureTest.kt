// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.capture

import it.manentlabs.app.audio.VoiceDetector
import it.manentlabs.app.voice.capture.CommandCapture.Event
import it.manentlabs.app.voice.wakeword.WakeWordScorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives the capture with made-up scores. The audio's sample values carry
 * the scores: each sample is the speech probability × 1000 for the voice
 * detector, and the wake scorer fires on frames that hold [WAKE] samples.
 */
class CommandCaptureTest {
    private class ScriptedWake(private val words: Int) : WakeWordScorer {
        val firing = mutableMapOf<Short, Int>()

        override fun score(frame: ShortArray): FloatArray = FloatArray(words) { w ->
            val word = firing[frame[0]]
            if (word == w || (word == BOTH && w == 1)) 0.9f else if (word == BOTH) 0.6f else 0.1f
        }

        override fun reset() = Unit

        override fun close() = Unit
    }

    private class ScriptedVoice : VoiceDetector {
        override fun score(frame: ShortArray): Float = frame[0] / 1000f
        override fun reset() = Unit
        override fun close() = Unit
    }

    private val wake = ScriptedWake(words = 2).apply { firing[WAKE] = 0; firing[WAKE_2] = 1; firing[WAKE_BOTH] = BOTH }
    private val capture = CommandCapture(wake, ScriptedVoice())

    private fun audio(ms: Long, value: Short) = ShortArray((ms * 16).toInt()) { value }

    private fun push(vararg parts: ShortArray): List<Event> = parts.flatMap { capture.push(it) }

    @Test
    fun `nothing happens on quiet audio`() {
        assertTrue(push(audio(5_000, QUIET)).isEmpty())
    }

    @Test
    fun `a command ends after speech and 900 ms of silence`() {
        val events = push(audio(80, WAKE), audio(1_024, SPEECH), audio(2_000, QUIET))
        assertEquals(Event.Heard(0), events[0])
        val command = events[1] as Event.Command
        assertEquals(0, command.word)
        // The firing frame, the speech, then silence up to the 900 ms that ended it (whole 32 ms frames).
        val silence = 29 * 512
        assertEquals(1280 + 16_384 + silence, command.pcm.size)
        assertEquals(WAKE, command.pcm[0])
        assertEquals(SPEECH, command.pcm[1280])
        assertFalse(capture.capturing)
    }

    @Test
    fun `wake word and command in one push keep their order`() {
        val joined = audio(80, WAKE) + audio(500, SPEECH) + audio(1_000, QUIET)
        val events = push(joined)
        assertEquals(2, events.size)
        assertTrue(events[1] is Event.Command)
    }

    @Test
    fun `no speech within 3 s drops the command`() {
        val events = push(audio(80, WAKE), audio(2_900, QUIET))
        assertEquals(listOf<Event>(Event.Heard(0)), events)
        assertTrue(capture.capturing)
        assertEquals(listOf<Event>(Event.Dropped(0)), push(audio(200, QUIET)))
    }

    @Test
    fun `scores between the thresholds hold the silence count`() {
        val events = push(audio(80, WAKE), audio(500, SPEECH), audio(3_000, MURMUR))
        assertEquals(1, events.size)
        assertTrue(capture.capturing)
        assertTrue(push(audio(1_000, QUIET))[0] is Event.Command)
    }

    @Test
    fun `murmur alone counts as silence before any speech`() {
        val events = push(audio(80, WAKE), audio(3_100, MURMUR))
        assertEquals(Event.Dropped(0), events.last())
    }

    @Test
    fun `a command stops at 8 s`() {
        val events = push(audio(80, WAKE), audio(10_000, SPEECH))
        val command = events[1] as Event.Command
        assertEquals(128_000, command.pcm.size)
    }

    @Test
    fun `wake words are ignored for 2 s after a command`() {
        push(audio(80, WAKE), audio(500, SPEECH), audio(1_000, QUIET))
        assertTrue(push(audio(1_000, WAKE)).isEmpty())
        assertTrue(push(audio(1_200, QUIET)).isEmpty())
        assertTrue(Event.Heard(0) in push(audio(160, WAKE)))
    }

    @Test
    fun `the highest score wins when two words fire`() {
        assertEquals(Event.Heard(1), push(audio(80, WAKE_BOTH)).single())
        assertEquals(Event.Heard(1), CommandCapture(wake, ScriptedVoice()).push(audio(80, WAKE_2)).single())
    }

    @Test
    fun `reset drops the command being captured`() {
        push(audio(80, WAKE), audio(500, SPEECH))
        assertTrue(capture.capturing)
        capture.reset()
        assertFalse(capture.capturing)
        assertTrue(push(audio(2_000, QUIET)).isEmpty())
        assertEquals(Event.Heard(0), push(audio(80, WAKE)).single())
    }

    @Test
    fun `audio arriving in odd-sized pieces gives the same result`() {
        val all = audio(80, WAKE) + audio(700, SPEECH) + audio(1_500, QUIET)
        val whole = CommandCapture(ScriptedWake(2).apply { firing[WAKE] = 0 }, ScriptedVoice()).push(all)
        val pieces = CommandCapture(ScriptedWake(2).apply { firing[WAKE] = 0 }, ScriptedVoice())
        val split = all.toList().chunked(321).flatMap { pieces.push(it.toShortArray()) }
        assertEquals(whole.size, split.size)
        assertEquals((whole[1] as Event.Command).pcm.toList(), (split[1] as Event.Command).pcm.toList())
    }

    private companion object {
        const val BOTH = 99
        const val QUIET: Short = 50
        const val MURMUR: Short = 400
        const val SPEECH: Short = 900
        const val WAKE: Short = 7_777
        const val WAKE_2: Short = 7_778
        const val WAKE_BOTH: Short = 7_779
    }
}
