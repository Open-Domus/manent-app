// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

/**
 * Whether a closed part keeps its audio. Parts with nothing said are filed
 * silent: their timing reaches the gateway, their audio is deleted. The part
 * right after one that ended mid-speech always keeps its audio, since the
 * rest of a cut sentence may be too short to count as speech on its own.
 */
object FilingPolicy {
    data class Decision(
        /** File it without audio. */
        val silent: Boolean,
        /** Remember for the next part of the same recording. */
        val endsInSpeech: Boolean,
        /** It counts as speech for an all-day episode's quiet clock. */
        val heldSpeech: Boolean,
    )

    fun decide(hasAudio: Boolean, durationMs: Long, verdict: SpeechVerdict, previousEndedInSpeech: Boolean): Decision =
        when {
            !hasAudio || durationMs <= 0 -> Decision(silent = true, endsInSpeech = false, heldSpeech = false)
            verdict is SpeechVerdict.Speech -> Decision(silent = false, endsInSpeech = verdict.atEnd, heldSpeech = true)
            verdict == SpeechVerdict.Unknown -> Decision(silent = false, endsInSpeech = true, heldSpeech = true)
            else -> Decision(silent = !previousEndedInSpeech, endsInSpeech = false, heldSpeech = false)
        }
}
