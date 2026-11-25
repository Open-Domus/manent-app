// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

import it.manentlabs.app.recording.RecoveryPlan.DiskPart
import it.manentlabs.app.recording.RecoveryPlan.Part
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryPlanTest {
    private fun session(filed: Int = 0, lastEnd: Long? = null, created: Boolean = false) =
        OutboxSession("s", null, 1_000_000, "UTC", 0, "en", false, created = created, filedCount = filed, lastFiledEndMs = lastEnd)

    @Test
    fun `files the part left on disk as interrupted, sized from its bytes`() {
        val plan = RecoveryPlan.of(session(filed = 2, lastEnd = 1_120_000), mapOf(2 to DiskPart(40_000, 1_140_000)))
        assertEquals(listOf(Part(2, 1_130_000, 10_000, silent = false, interrupted = true)), plan.parts)
        assertEquals(3, plan.chunkCount)
        assertEquals(1_140_000, plan.endedAtMs)
        assertFalse(plan.discard)
    }

    @Test
    fun `a part never starts before the previous one ends`() {
        val plan = RecoveryPlan.of(session(filed = 1, lastEnd = 1_060_000), mapOf(1 to DiskPart(240_000, 1_070_000)))
        assertEquals(1_060_000, plan.parts.single().startedAtMs)
        assertEquals(1_120_000, plan.endedAtMs)
    }

    @Test
    fun `holes become silent zero-length parts`() {
        val plan = RecoveryPlan.of(session(), mapOf(0 to DiskPart(0, 1_001_000), 2 to DiskPart(4_000, 1_005_000)))
        assertEquals(
            listOf(Part(0, 1_000_000, 0, true, false), Part(1, 1_000_000, 0, true, false), Part(2, 1_004_000, 1_000, false, true)),
            plan.parts,
        )
        assertEquals(3, plan.chunkCount)
    }

    @Test
    fun `an unplayable part keeps its estimated length but is filed silent and interrupted`() {
        val files = mapOf(2 to DiskPart(40_000, 1_140_000, playable = false))
        val plan = RecoveryPlan.of(session(filed = 2, lastEnd = 1_120_000, created = true), files)
        assertEquals(listOf(Part(2, 1_130_000, 10_000, silent = true, interrupted = true)), plan.parts)
        assertEquals(3, plan.chunkCount)
        assertEquals(1_140_000, plan.endedAtMs)
    }

    @Test
    fun `nothing on disk closes at the last filed end`() {
        val plan = RecoveryPlan.of(session(filed = 4, lastEnd = 1_200_000, created = true), emptyMap())
        assertTrue(plan.parts.isEmpty())
        assertEquals(4, plan.chunkCount)
        assertEquals(1_200_000, plan.endedAtMs)
        assertFalse(plan.discard)
    }

    @Test
    fun `a recording with nothing in it that the gateway never saw is discarded`() {
        assertTrue(RecoveryPlan.of(session(), emptyMap()).discard)
        assertTrue(RecoveryPlan.of(session(), mapOf(0 to DiskPart(0, 0))).discard)
        assertFalse(RecoveryPlan.of(session(created = true), emptyMap()).discard)
        assertEquals(1_000_000, RecoveryPlan.of(session(), emptyMap()).endedAtMs)
    }
}
