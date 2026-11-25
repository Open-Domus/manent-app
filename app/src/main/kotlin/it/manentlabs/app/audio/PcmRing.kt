// SPDX-License-Identifier: MIT

package it.manentlabs.app.audio

/** Keeps the most recent [capacity] samples of a stream, oldest dropped first. */
class PcmRing(val capacity: Int) {
    private val data = ShortArray(capacity)
    private var start = 0

    /** How many samples are held, up to [capacity]. */
    var size = 0
        private set

    fun push(pcm: ShortArray, offset: Int = 0, count: Int = pcm.size - offset) {
        val skip = maxOf(0, count - capacity)
        for (i in offset + skip until offset + count) {
            val end = (start + size) % capacity
            data[end] = pcm[i]
            if (size < capacity) size++ else start = (start + 1) % capacity
        }
    }

    /** The held samples, oldest first. */
    fun snapshot(): ShortArray = ShortArray(size) { data[(start + it) % capacity] }

    fun clear() {
        start = 0
        size = 0
    }
}
