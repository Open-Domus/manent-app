// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.capture

import it.manentlabs.app.audio.PcmFormat
import it.manentlabs.app.audio.VoiceDetector
import it.manentlabs.app.voice.wakeword.WakeWordEngine
import it.manentlabs.app.voice.wakeword.WakeWordScorer

/** The timings that decide where a spoken command starts and ends, all in audio time. */
object CapturePolicy {
    /** A word fires when its score reaches this. */
    const val WAKE_THRESHOLD = 0.5f
    const val NO_SPEECH_MS = 3_000L
    const val END_SILENCE_MS = 900L
    const val MAX_COMMAND_MS = 8_000L
    const val COOL_DOWN_MS = 2_000L
    const val SPEECH_START = 0.5f
    const val SPEECH_END = 0.35f
}

/**
 * Listens for wake words in 16 kHz audio and cuts out the command that
 * follows one. Feed it audio in order with [push]; it says when a word is
 * heard and when a command is complete or dropped.
 *
 * While listening, audio is scored in 80 ms frames. When a word fires, that
 * frame starts the command, so a word and command said in one breath keep
 * the command's first syllables. The command then runs until speech has
 * been followed by 0.9 s of silence, or for 8 s at most; if nobody speaks
 * within 3 s it was a false start and is dropped. A detector score between
 * the start and end thresholds leaves the silence count as it is. After any
 * ending, wake words are ignored for 2 s.
 *
 * All state is audio time, so the same audio always gives the same result.
 * Not thread-safe; a model failure propagates, and the caller then [reset]s.
 */
class CommandCapture(private val wake: WakeWordScorer, private val voice: VoiceDetector) {
    sealed interface Event {
        /** Word [word] (its index in the scorer's output) was heard: the command follows. */
        data class Heard(val word: Int) : Event

        /** The command after [word], as 16 kHz mono PCM. */
        class Command(val word: Int, val pcm: ShortArray) : Event

        /** Nothing was said after [word]; nothing is sent. */
        data class Dropped(val word: Int) : Event
    }

    private val frame = ShortArray(WakeWordEngine.FRAME_SAMPLES)
    private var filled = 0
    private var coolDownSamples = 0L
    private var capture: Capture? = null

    /** Whether a command is being captured. */
    val capturing: Boolean get() = capture != null

    fun push(pcm: ShortArray): List<Event> {
        val events = mutableListOf<Event>()
        var i = 0
        while (i < pcm.size) {
            val open = capture
            if (open != null) {
                i += open.take(pcm, i, events)
                continue
            }
            val n = minOf(frame.size - filled, pcm.size - i)
            pcm.copyInto(frame, filled, i, i + n)
            filled += n
            i += n
            if (filled == frame.size) {
                filled = 0
                listen(events)
            }
        }
        return events
    }

    /** Drops any command being captured and forgets all audio heard, for audio that does not follow on. */
    fun reset() {
        capture = null
        filled = 0
        coolDownSamples = 0
        wake.reset()
        voice.reset()
    }

    private fun listen(events: MutableList<Event>) {
        val scores = wake.score(frame)
        if (coolDownSamples > 0) {
            coolDownSamples -= frame.size
            return
        }
        var best = -1
        for (w in scores.indices) {
            if (scores[w] >= CapturePolicy.WAKE_THRESHOLD && (best < 0 || scores[w] > scores[best])) best = w
        }
        if (best < 0) return
        events += Event.Heard(best)
        voice.reset()
        capture = Capture(best).also { it.start(frame) }
    }

    private fun end(event: Event, events: MutableList<Event>) {
        events += event
        capture = null
        filled = 0
        wake.reset()
        coolDownSamples = PcmFormat.msToSamples(CapturePolicy.COOL_DOWN_MS)
    }

    private inner class Capture(val word: Int) {
        private val audio = ShortArray(PcmFormat.msToSamples(CapturePolicy.MAX_COMMAND_MS).toInt())
        private var length = 0
        private val voiceFrame = ShortArray(VoiceDetector.FRAME_SAMPLES)
        private var voiceFilled = 0
        private var speech = false
        private var silenceSamples = 0L

        /** The frame the word fired in opens the command; it is not scored, as it is mostly the word itself. */
        fun start(first: ShortArray) {
            first.copyInto(audio)
            length = first.size
        }

        /** Takes samples from [pcm] at [from] until the command ends or the samples run out; how many it took. */
        fun take(pcm: ShortArray, from: Int, events: MutableList<Event>): Int {
            var i = from
            while (i < pcm.size) {
                val n = minOf(pcm.size - i, audio.size - length, voiceFrame.size - voiceFilled)
                pcm.copyInto(audio, length, i, i + n)
                pcm.copyInto(voiceFrame, voiceFilled, i, i + n)
                length += n
                voiceFilled += n
                i += n
                if (voiceFilled == voiceFrame.size) {
                    voiceFilled = 0
                    judge(voice.score(voiceFrame))?.let {
                        end(it, events)
                        return i - from
                    }
                }
                if (length == audio.size) {
                    end(if (speech) command() else Event.Dropped(word), events)
                    return i - from
                }
            }
            return i - from
        }

        private fun judge(probability: Float): Event? {
            when {
                probability >= CapturePolicy.SPEECH_START -> {
                    speech = true
                    silenceSamples = 0
                }
                probability < CapturePolicy.SPEECH_END || !speech -> silenceSamples += VoiceDetector.FRAME_SAMPLES
            }
            val silenceMs = PcmFormat.samplesToMs(silenceSamples)
            return when {
                speech && silenceMs >= CapturePolicy.END_SILENCE_MS -> command()
                !speech && silenceMs >= CapturePolicy.NO_SPEECH_MS -> Event.Dropped(word)
                else -> null
            }
        }

        private fun command() = Event.Command(word, audio.copyOf(length))
    }
}
