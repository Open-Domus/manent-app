// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

/**
 * Decides when a wearable part should close, so that the gateway gets each
 * sentence seconds after it ends and cuts fall between sentences.
 *
 * A part that held speech closes at the first pause once it is long enough;
 * a part of quiet alone runs to the full length. After a detector failure
 * only the length counts, for the rest of the run.
 */
class SentenceSplitter {
    private var heardSpeech = false
    private var quietMs = 0L
    private var detectorBroken = false

    /** One 32 ms detector frame of the open part. */
    fun onFrame(probability: Float) {
        if (probability >= SPEECH) heardSpeech = true
        quietMs = if (probability < QUIET) quietMs + FRAME_MS else 0
    }

    /** The detector failed; from now on parts are cut by length only. */
    fun onDetectorFailed() {
        detectorBroken = true
    }

    fun shouldCut(partMs: Long): Boolean =
        partMs >= RecordingLimits.CHUNK_TARGET_MS ||
            (!detectorBroken && partMs >= MIN_PART_MS && heardSpeech && quietMs >= PAUSE_MS)

    /** A new part opened. */
    fun onPartOpened() {
        heardSpeech = false
        quietMs = 0
    }

    private companion object {
        const val SPEECH = 0.5f
        const val QUIET = 0.35f
        const val FRAME_MS = 32L
        const val MIN_PART_MS = 10_000L
        const val PAUSE_MS = 700L
    }
}
