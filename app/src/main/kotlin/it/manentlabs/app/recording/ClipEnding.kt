// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

/**
 * When a marked moment's clip ends: at a 1.2 s pause once 2 s have passed
 * since the mark, or 60 s after the mark whatever is said. Only audio heard
 * after the mark counts. Without a detector only the 60 s limit applies; a
 * frame the detector failed on counts as speech.
 */
class ClipEnding(private val hasDetector: Boolean) {
    private var sinceMarkMs = 0L
    private var quietMs = 0L

    /** [ms] of audio after the mark went into the clip. */
    fun onAudio(ms: Long) {
        sinceMarkMs += ms
    }

    /** One 32 ms detector frame after the mark; null when the detector failed on it. */
    fun onFrame(probability: Float?) {
        quietMs = if (probability != null && probability < QUIET) quietMs + FRAME_MS else 0
    }

    fun shouldEnd(): Boolean =
        sinceMarkMs >= MAX_MS || (hasDetector && sinceMarkMs >= MIN_MS && quietMs >= PAUSE_MS)

    private companion object {
        const val QUIET = 0.35f
        const val FRAME_MS = 32L
        const val MIN_MS = 2_000L
        const val PAUSE_MS = 1_200L
        const val MAX_MS = 60_000L
    }
}
