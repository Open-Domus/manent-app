// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

/**
 * Finds a stretch of speech in a run of detector scores, with the defaults of
 * Silero's reference "speech timestamps": a segment starts at a frame of 0.5
 * or more, carries on through frames of 0.35 or more, ends after 100 ms below
 * that, and counts once it lasts 250 ms.
 */
class SpeechSegments {
    private var frame = 0L
    private var startFrame = -1L
    private var lastVoicedFrame = -1L
    private var quietFrames = 0

    /** True once speech has been found; feed one 32 ms frame's score at a time. */
    var found = false
        private set

    fun onFrame(probability: Float): Boolean {
        if (startFrame < 0) {
            if (probability >= START) {
                startFrame = frame
                lastVoicedFrame = frame
                quietFrames = 0
            }
        } else if (probability >= CONTINUE) {
            lastVoicedFrame = frame
            quietFrames = 0
        } else if (++quietFrames * FRAME_MS >= END_QUIET_MS) {
            startFrame = -1
        }
        if (startFrame >= 0 && (lastVoicedFrame - startFrame + 1) * FRAME_MS >= MIN_SPEECH_MS) found = true
        frame++
        return found
    }

    private companion object {
        const val START = 0.5f
        const val CONTINUE = 0.35f
        const val FRAME_MS = 32L
        const val END_QUIET_MS = 100L
        const val MIN_SPEECH_MS = 250L
    }
}
