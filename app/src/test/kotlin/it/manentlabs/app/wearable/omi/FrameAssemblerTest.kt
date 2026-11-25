// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable.omi

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameAssemblerTest {
    private val assembler = FrameAssembler()

    private fun packet(counter: Int, index: Int, vararg payload: Int) =
        byteArrayOf(counter.toByte(), (counter shr 8).toByte(), index.toByte()) + payload.map { it.toByte() }.toByteArray()

    @Test
    fun `a frame is emitted when the next one starts in order`() {
        assertNull(assembler.accept(packet(1, 0, 1, 2)))
        assertNull(assembler.accept(packet(2, 1, 3)))
        assertArrayEquals(byteArrayOf(1, 2, 3), assembler.accept(packet(3, 0, 9)))
        assertEquals(0, assembler.framesLost)
    }

    @Test
    fun `short packets are ignored`() {
        assertNull(assembler.accept(byteArrayOf(1, 0)))
        assertNull(assembler.accept(packet(1, 0, 5)))
        assertArrayEquals(byteArrayOf(5), assembler.accept(packet(2, 0)))
    }

    @Test
    fun `a gap in the counter before a frame start drops the frame in progress`() {
        assembler.accept(packet(1, 0, 1))
        assertNull(assembler.accept(packet(3, 0, 2)))
        assertEquals(1, assembler.framesLost)
        assertArrayEquals(byteArrayOf(2), assembler.accept(packet(4, 0)))
    }

    @Test
    fun `fragments before any frame start are discarded but their counter counts`() {
        assertNull(assembler.accept(packet(7, 2, 1)))
        assertNull(assembler.accept(packet(8, 0, 4)))
        assertArrayEquals(byteArrayOf(4), assembler.accept(packet(9, 0)))
        assertEquals(0, assembler.framesLost)
    }

    @Test
    fun `a skipped fragment index drops the frame and waits for the next start`() {
        assembler.accept(packet(1, 0, 1))
        assertNull(assembler.accept(packet(2, 2, 2)))
        assertEquals(1, assembler.framesLost)
        assertNull(assembler.accept(packet(3, 3, 3)))
        assertNull(assembler.accept(packet(4, 0, 4)))
        assertArrayEquals(byteArrayOf(4), assembler.accept(packet(5, 0)))
        assertEquals(1, assembler.framesLost)
    }

    @Test
    fun `an out-of-order fragment drops the frame`() {
        assembler.accept(packet(1, 0, 1))
        assertNull(assembler.accept(packet(5, 1, 2)))
        assertEquals(1, assembler.framesLost)
    }

    @Test
    fun `the counter wraps at 65536`() {
        assembler.accept(packet(65535, 0, 1))
        assertArrayEquals(byteArrayOf(1), assembler.accept(packet(0, 0)))
    }

    @Test
    fun `an empty frame is never emitted`() {
        assembler.accept(packet(1, 0))
        assertNull(assembler.accept(packet(2, 0, 1)))
        assertEquals(0, assembler.framesLost)
    }

    @Test
    fun `reset drops the frame in progress and the lost count`() {
        assembler.accept(packet(1, 0, 1))
        assembler.accept(packet(3, 0, 2))
        assembler.reset()
        assertEquals(0, assembler.framesLost)
        assertNull(assembler.accept(packet(4, 1, 3)))
        assembler.accept(packet(5, 0, 4))
        assertArrayEquals(byteArrayOf(4), assembler.accept(packet(6, 0)))
    }
}
