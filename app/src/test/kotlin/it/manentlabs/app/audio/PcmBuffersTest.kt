// SPDX-License-Identifier: MIT

package it.manentlabs.app.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PcmBuffersTest {
    @Test
    fun `frame splitter recuts pieces into whole frames`() {
        val splitter = FrameSplitter(4)
        val frames = mutableListOf<ShortArray>()
        splitter.push(shortArrayOf(1, 2, 3)) { frames += it }
        splitter.push(shortArrayOf(4, 5, 6, 7, 8, 9)) { frames += it }
        assertEquals(2, frames.size)
        assertArrayEquals(shortArrayOf(1, 2, 3, 4), frames[0])
        assertArrayEquals(shortArrayOf(5, 6, 7, 8), frames[1])
        splitter.clear()
        splitter.push(shortArrayOf(10, 11, 12, 13)) { frames += it }
        assertArrayEquals(shortArrayOf(10, 11, 12, 13), frames[2])
    }

    @Test
    fun `ring keeps the latest samples oldest first`() {
        val ring = PcmRing(4)
        ring.push(shortArrayOf(1, 2, 3))
        assertArrayEquals(shortArrayOf(1, 2, 3), ring.snapshot())
        ring.push(shortArrayOf(4, 5))
        assertArrayEquals(shortArrayOf(2, 3, 4, 5), ring.snapshot())
        ring.push(shortArrayOf(6, 7, 8, 9, 10, 11))
        assertArrayEquals(shortArrayOf(8, 9, 10, 11), ring.snapshot())
        ring.clear()
        assertEquals(0, ring.snapshot().size)
    }

    @Test
    fun `decimation only for whole multiples of 16 kHz`() {
        assertEquals(3, PcmFormat.decimation(48_000))
        assertEquals(1, PcmFormat.decimation(16_000))
        assertEquals(2, PcmFormat.decimation(32_000))
        assertNull(PcmFormat.decimation(44_100))
        assertNull(PcmFormat.decimation(8_000))
    }
}
