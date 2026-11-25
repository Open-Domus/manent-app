// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable.omi

import it.manentlabs.app.wearable.RecorderState
import it.manentlabs.app.wearable.RecordingInput
import org.junit.Assert.assertEquals
import org.junit.Test

class PendantPolicyTest {
    private val idle = RecorderState.Idle
    private val wearable = RecorderState.Active(RecordingInput.WEARABLE, ambient = false)
    private val phone = RecorderState.Active(RecordingInput.PHONE, ambient = false)
    private val ambient = RecorderState.Active(RecordingInput.WEARABLE, ambient = true)

    @Test
    fun `tap decisions`() {
        assertEquals(TapAction.IGNORE, PendantPolicy.onTap(startPending = true, captureAllDay = true, recorder = idle))
        assertEquals(TapAction.MARK, PendantPolicy.onTap(false, true, idle))
        assertEquals(TapAction.MARK, PendantPolicy.onTap(false, true, ambient))
        assertEquals(TapAction.STOP, PendantPolicy.onTap(false, true, phone))
        assertEquals(TapAction.STOP, PendantPolicy.onTap(false, false, wearable))
        assertEquals(TapAction.STOP, PendantPolicy.onTap(false, false, phone))
        assertEquals(TapAction.START, PendantPolicy.onTap(false, false, idle))
    }

    @Test
    fun `all-day automation`() {
        assertEquals(AllDayAction.START_AMBIENT, PendantPolicy.allDay(captureAllDay = true, ready = true, recorder = idle))
        assertEquals(AllDayAction.NONE, PendantPolicy.allDay(true, false, idle))
        assertEquals(AllDayAction.NONE, PendantPolicy.allDay(true, true, phone))
        assertEquals(AllDayAction.STOP, PendantPolicy.allDay(false, true, ambient))
        assertEquals(AllDayAction.STOP, PendantPolicy.allDay(false, false, ambient))
        assertEquals(AllDayAction.NONE, PendantPolicy.allDay(false, true, wearable))
        assertEquals(AllDayAction.NONE, PendantPolicy.allDay(false, true, idle))
    }
}
