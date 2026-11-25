// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable.omi

import it.manentlabs.app.wearable.WearableSignal
import org.junit.Assert.assertEquals
import org.junit.Test

class HapticPatternTest {
    @Test
    fun `each signal writes the firmware's pulse bytes 250 ms apart`() {
        assertEquals(HapticPattern(listOf<Byte>(1), 250), HapticPattern.of(WearableSignal.STARTED))
        assertEquals(HapticPattern(listOf<Byte>(1, 1), 250), HapticPattern.of(WearableSignal.STOPPED))
        assertEquals(HapticPattern(listOf<Byte>(1), 250), HapticPattern.of(WearableSignal.MARKED))
        assertEquals(HapticPattern(listOf<Byte>(3), 250), HapticPattern.of(WearableSignal.FAILED))
    }

    @Test
    fun `find is three long pulses 700 ms apart`() {
        assertEquals(HapticPattern(listOf<Byte>(3, 3, 3), 700), HapticPattern.FIND)
    }
}
