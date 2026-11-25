// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

/**
 * The quiet clock of an all-day episode: it closes after 90 s without a part
 * holding speech, counted from the episode's start and from the end of each
 * part that held speech.
 */
class EpisodeClock(startMs: Long) {
    private var lastSpeechMs = startMs

    fun onSpeechPart(endMs: Long) {
        lastSpeechMs = maxOf(lastSpeechMs, endMs)
    }

    fun expired(nowMs: Long): Boolean = nowMs - lastSpeechMs >= RecordingLimits.EPISODE_QUIET_MS
}
