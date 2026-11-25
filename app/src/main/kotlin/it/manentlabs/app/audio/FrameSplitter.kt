// SPDX-License-Identifier: MIT

package it.manentlabs.app.audio

/**
 * Re-cuts PCM arriving in pieces of any length into whole frames of [size]
 * samples, keeping the remainder for the next call.
 */
class FrameSplitter(private val size: Int = VoiceDetector.FRAME_SAMPLES) {
    private val pending = ShortArray(size)
    private var filled = 0

    /** Adds [count] samples of [pcm] from [offset] and hands each completed frame to [onFrame]. */
    inline fun push(pcm: ShortArray, offset: Int = 0, count: Int = pcm.size - offset, onFrame: (ShortArray) -> Unit) {
        var i = offset
        val end = offset + count
        while (i < end) {
            val taken = take(pcm, i, end - i)
            i += taken
            frameIfFull()?.let(onFrame)
        }
    }

    /** Drops a partial frame, for audio that does not follow on. */
    fun clear() {
        filled = 0
    }

    @PublishedApi
    internal fun take(pcm: ShortArray, offset: Int, available: Int): Int {
        val n = minOf(size - filled, available)
        System.arraycopy(pcm, offset, pending, filled, n)
        filled += n
        return n
    }

    @PublishedApi
    internal fun frameIfFull(): ShortArray? {
        if (filled < size) return null
        filled = 0
        return pending.copyOf()
    }
}
