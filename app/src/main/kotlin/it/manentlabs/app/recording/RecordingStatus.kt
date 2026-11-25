// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

import it.manentlabs.app.wearable.RecordingInput

/** What the recorder is doing, as the screen and the notification show it. Memory only. */
sealed interface RecordingStatus {
    data object Idle : RecordingStatus

    data class Active(
        val sessionId: String,
        val title: String?,
        val input: RecordingInput,
        val ambient: Boolean,
        /** Recorded time before the current run. */
        val recordedBeforeMs: Long,
        /** When the current run started, on the elapsed-realtime clock; null while paused. */
        val runningSince: Long?,
        val partsFiled: Int,
        /** Paused because the input went away, not by the user. */
        val inputLost: Boolean,
    ) : RecordingStatus {
        val paused: Boolean get() = runningSince == null

        /** Recorded time at [now] on the elapsed-realtime clock; pauses do not count. */
        fun elapsedMs(now: Long): Long = recordedBeforeMs + (runningSince?.let { maxOf(0, now - it) } ?: 0)
    }
}

/** A request to record; [ambient] for an all-day episode rather than one someone asked for. */
data class StartRequest(val input: RecordingInput, val title: String?, val ambient: Boolean)

/**
 * What a new start request does given what the recorder is doing. Only one
 * recording runs at a time, but one someone asks for takes precedence over
 * an all-day episode.
 */
object StartPolicy {
    enum class Phase { IDLE, STARTING, RUNNING, CLOSING }

    enum class Decision {
        /** Nothing runs: start it. */
        BEGIN,

        /** Close the running episode, then start this. */
        REPLACE_EPISODE,

        /** An episode is closing and the next was due: start this instead. */
        TAKE_NEXT_EPISODE,

        IGNORE,
    }

    fun decide(phase: Phase, currentAmbient: Boolean, queuedAmbient: Boolean?, request: StartRequest): Decision = when {
        phase == Phase.IDLE -> Decision.BEGIN
        request.ambient -> Decision.IGNORE
        phase == Phase.CLOSING -> if (queuedAmbient == true) Decision.TAKE_NEXT_EPISODE else Decision.IGNORE
        currentAmbient -> Decision.REPLACE_EPISODE
        else -> Decision.IGNORE
    }
}
