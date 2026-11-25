// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable

import kotlinx.coroutines.flow.StateFlow

/**
 * What the pendant's button and the "Capture all day" automation need from
 * the recorder. The wearable side drives recordings only through this.
 */
interface RecorderControl {
    val state: StateFlow<RecorderState>

    /** "Capture all day": record every conversation the wearable hears, one recording each. */
    val captureAllDay: StateFlow<Boolean>

    fun setCaptureAllDay(on: Boolean)

    /** Starts a recording from the wearable, untitled. False when the system refused to start it. */
    fun startWearableRecording(): Boolean

    /** Stops whatever recording is active, from any input. */
    fun stop()

    /** Starts listening for conversations to capture all day. False when refused. */
    fun startAmbient(): Boolean

    /** Keeps the moment around now as a note. False when there is nothing to mark, as between conversations. */
    suspend fun markMoment(): Boolean
}

sealed interface RecorderState {
    data object Idle : RecorderState

    /** [ambient] for an all-day capture episode rather than a recording someone started. */
    data class Active(val input: RecordingInput, val ambient: Boolean) : RecorderState
}

enum class RecordingInput { WEARABLE, PHONE }
