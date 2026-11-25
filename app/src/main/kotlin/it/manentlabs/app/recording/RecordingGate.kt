// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

import it.manentlabs.app.gateway.RecordingAvailability

/**
 * Whether a recording may start. Recordings exist to reach an Omnesis
 * gateway, so one must be paired and its last answer must be that it takes
 * recordings.
 */
enum class RecordingGate {
    OPEN,
    NOT_PAIRED,
    TRANSCRIPTION_OFF,
    UNAVAILABLE,
    ;

    companion object {
        fun of(paired: Boolean, availability: RecordingAvailability): RecordingGate = when {
            !paired -> NOT_PAIRED
            availability == RecordingAvailability.AVAILABLE -> OPEN
            availability == RecordingAvailability.TRANSCRIPTION_OFF -> TRANSCRIPTION_OFF
            else -> UNAVAILABLE
        }
    }
}
