// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

/**
 * Hands out a recording's part numbers, 0 upwards with no holes: the gateway
 * expects every number below the final count. A number taken for a file that
 * was never used is given back, which only works for the latest one.
 * Thread-safe.
 */
class ChunkNumbers(first: Int = 0) {
    private var next = first

    /** How many numbers are in use: the recording's part count. */
    val count: Int @Synchronized get() = next

    @Synchronized
    fun reserve(): Int = next++

    /** Returns [seq] if it is the latest number handed out; anything else stays used. */
    @Synchronized
    fun giveBack(seq: Int): Boolean {
        if (seq != next - 1) return false
        next--
        return true
    }
}
