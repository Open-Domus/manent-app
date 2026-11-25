// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChunkHolesTest {
    @Test
    fun `nothing to fill when every number was filed`() {
        assertTrue(ChunkHoles.placeholders(3, setOf(0, 1, 2), emptyMap(), 0).isEmpty())
        assertTrue(ChunkHoles.placeholders(0, emptySet(), emptyMap(), 0).isEmpty())
    }

    @Test
    fun `a part that failed to file becomes silence in its own place`() {
        val failed = ClosedPart(1, File("chunk-00001.m4a"), 5_000, 2_000, interrupted = false)
        val holes = ChunkHoles.placeholders(3, setOf(0, 2), mapOf(1 to failed), 9_000)
        assertEquals(listOf(ClosedPart(1, null, 5_000, 2_000, interrupted = true)), holes)
    }

    @Test
    fun `a number with no known part is a zero-length placeholder at the fallback start`() {
        val holes = ChunkHoles.placeholders(4, setOf(0, 1), emptyMap(), 9_000)
        assertEquals(
            listOf(ClosedPart(2, null, 9_000, 0, interrupted = true), ClosedPart(3, null, 9_000, 0, interrupted = true)),
            holes,
        )
    }
}
