// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable.omi

import it.manentlabs.app.wearable.WearableSignal

/**
 * A sequence of motor pulses. The firmware plays one pulse per write of a
 * single byte (1 = 100 ms, 2 = 300 ms, 3 = 500 ms), so several pulses are
 * several writes [gapMs] apart.
 */
data class HapticPattern(val pulses: List<Byte>, val gapMs: Long = 250) {
    companion object {
        private const val SHORT: Byte = 1
        private const val LONG: Byte = 3

        val STARTED = HapticPattern(listOf(SHORT))
        val STOPPED = HapticPattern(listOf(SHORT, SHORT))
        val MARKED = HapticPattern(listOf(SHORT))
        val FAILED = HapticPattern(listOf(LONG))

        /** Long and spaced out, so the pendant can be found by feel or sound. */
        val FIND = HapticPattern(listOf(LONG, LONG, LONG), gapMs = 700)

        fun of(signal: WearableSignal): HapticPattern = when (signal) {
            WearableSignal.STARTED -> STARTED
            WearableSignal.STOPPED -> STOPPED
            WearableSignal.MARKED -> MARKED
            WearableSignal.FAILED -> FAILED
        }
    }
}
