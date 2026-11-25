// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

import it.manentlabs.app.audio.PcmFormat
import it.manentlabs.app.audio.VoiceDetector

/** What a closed part's speech check found. */
sealed interface SpeechVerdict {
    /** [atEnd] when its last 2 s hold speech too, so a sentence may carry over into the next part. */
    data class Speech(val atEnd: Boolean) : SpeechVerdict

    data object NoSpeech : SpeechVerdict

    /** The check could not run. Treated as speech that runs to the end: a broken detector costs a transcription, never audio. */
    data object Unknown : SpeechVerdict
}

/** Runs the speech check over a part's decoded audio. */
object SpeechScan {
    private const val TAIL_MS = 2_000L

    /** Scans [pcm] (16 kHz mono) with [detector]; a detector failure gives [SpeechVerdict.Unknown]. */
    fun scan(pcm: ShortArray, detector: VoiceDetector): SpeechVerdict = try {
        if (!hasSpeech(pcm, 0, detector)) {
            SpeechVerdict.NoSpeech
        } else {
            val tailStart = maxOf(0, pcm.size - PcmFormat.msToSamples(TAIL_MS).toInt())
            SpeechVerdict.Speech(atEnd = hasSpeech(pcm, tailStart, detector))
        }
    } catch (_: Exception) {
        SpeechVerdict.Unknown
    }

    /** Whether [pcm] from [from] holds a speech segment; stops at the first one. */
    private fun hasSpeech(pcm: ShortArray, from: Int, detector: VoiceDetector): Boolean {
        detector.reset()
        val segments = SpeechSegments()
        val size = VoiceDetector.FRAME_SAMPLES
        var offset = from
        while (offset + size <= pcm.size) {
            if (segments.onFrame(detector.score(pcm.copyOfRange(offset, offset + size)))) return true
            offset += size
        }
        return false
    }
}
