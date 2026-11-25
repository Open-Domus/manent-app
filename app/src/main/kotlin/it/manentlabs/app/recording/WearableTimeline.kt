// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

/**
 * Lays a wearable's frames on the wall clock. A frame's audio is taken to end
 * when the frame arrived; the pendant goes quiet while nobody speaks, so a
 * long silence between frames is a gap rather than lost audio.
 */
class WearableTimeline(private val frameMs: Long = 20) {
    private var audioEndMs: Long? = null
    private var lastArrivalMs = 0L

    sealed interface Placement {
        /** The first frame of a run: a part opens at [startMs]. */
        data class Open(val startMs: Long) : Placement

        /** The frame follows on; [silenceMs] of silence go before it (zero when on time). */
        data class Follow(val silenceMs: Long) : Placement

        /**
         * Nothing came for over 2 s: the open part closes at [gapStartMs], the
         * [gapMs] of quiet is filed on its own, and a part opens at [resumeAtMs].
         */
        data class Gap(val gapStartMs: Long, val gapMs: Long, val resumeAtMs: Long) : Placement
    }

    fun place(arrivalMs: Long): Placement {
        val end = audioEndMs
        val frameStart = arrivalMs - frameMs
        val placement = when {
            end == null -> Placement.Open(frameStart)
            arrivalMs - lastArrivalMs > GAP_MS -> Placement.Gap(end, maxOf(0, frameStart - end), frameStart)
            frameStart - end > LATE_MS -> Placement.Follow(frameStart - end)
            else -> Placement.Follow(0)
        }
        audioEndMs = when (placement) {
            is Placement.Follow -> end!! + placement.silenceMs + frameMs
            else -> arrivalMs
        }
        lastArrivalMs = arrivalMs
        return placement
    }

    /** A new run starts: the next frame opens a part. */
    fun reset() {
        audioEndMs = null
    }

    private companion object {
        const val GAP_MS = 2_000L
        const val LATE_MS = 300L
    }
}
