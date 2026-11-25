// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

/**
 * Where a recording's audio comes from. A recording opens a run on start and
 * on each resume, and closes it on pause and stop; each run writes one or
 * more parts. Calls come from the recorder's control thread.
 */
interface InputSource {
    /** Starts a run with a new part. False when the input cannot record now. */
    fun open(): Boolean

    /** Ends the run. Returns once its last part has been handed to [InputEvents.onPartClosed]. */
    fun close()

    /** Frees the input for good, after the last [close]. */
    fun release()
}

/** What an input reports back, from its own thread. */
interface InputEvents {
    /** A part is complete. Called in part order. */
    fun onPartClosed(part: ClosedPart)

    /** The input went away mid-run; the run is over and its last part already reported. */
    fun onInputLost()
}
