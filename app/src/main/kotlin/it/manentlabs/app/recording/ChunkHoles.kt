// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

/**
 * The gateway finishes a recording only once it has every part number below
 * the final count. A part whose filing failed (a full disk, say) would leave
 * a hole it waits on forever, so on close each missing number is filed as a
 * silent, interrupted placeholder instead.
 */
object ChunkHoles {
    /**
     * Placeholders for the numbers below [count] not in [filed]. One whose
     * filing failed keeps that part's place and length from [failed]; any
     * other starts at [fallbackStartMs] and lasts 0 ms.
     */
    fun placeholders(count: Int, filed: Set<Int>, failed: Map<Int, ClosedPart>, fallbackStartMs: Long): List<ClosedPart> =
        (0 until count).filter { it !in filed }.map { seq ->
            failed[seq]?.copy(file = null, interrupted = true)
                ?: ClosedPart(seq, null, fallbackStartMs, 0, interrupted = true)
        }
}
