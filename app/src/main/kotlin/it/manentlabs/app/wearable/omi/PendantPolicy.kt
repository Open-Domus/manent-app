// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable.omi

import it.manentlabs.app.wearable.RecorderState

/** What a tap on the pendant does, decided from what is happening now. */
enum class TapAction { IGNORE, MARK, STOP, START }

/** What the "Capture all day" automation should be doing. */
enum class AllDayAction { START_AMBIENT, STOP, NONE }

object PendantPolicy {
    /**
     * A tap while a start is still unconfirmed is a double press and is
     * ignored. With all-day capture on, a tap marks a moment rather than
     * starting a separate recording, unless someone else's recording runs.
     */
    fun onTap(startPending: Boolean, captureAllDay: Boolean, recorder: RecorderState): TapAction = when {
        startPending -> TapAction.IGNORE
        captureAllDay && (recorder is RecorderState.Idle || (recorder as RecorderState.Active).ambient) -> TapAction.MARK
        recorder is RecorderState.Active -> TapAction.STOP
        else -> TapAction.START
    }

    fun allDay(captureAllDay: Boolean, ready: Boolean, recorder: RecorderState): AllDayAction = when {
        captureAllDay && ready && recorder is RecorderState.Idle -> AllDayAction.START_AMBIENT
        !captureAllDay && recorder is RecorderState.Active && recorder.ambient -> AllDayAction.STOP
        else -> AllDayAction.NONE
    }
}
