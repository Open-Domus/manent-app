// SPDX-License-Identifier: MIT

package it.manentlabs.app.audio

import java.io.Closeable

/**
 * Scores how likely each 32 ms frame of 16 kHz speech is to be a voice.
 * Frames must be fed in order; the detector remembers what came before.
 */
interface VoiceDetector : Closeable {
    /** The probability, 0 to 1, that [frame] ([FRAME_SAMPLES] samples) holds speech. Throws if the model fails. */
    fun score(frame: ShortArray): Float

    /** Forgets earlier frames, for audio that does not follow on from them. */
    fun reset()

    companion object {
        /** 32 ms at 16 kHz. */
        const val FRAME_SAMPLES = 512
        const val FRAME_MS = 32L
    }
}
