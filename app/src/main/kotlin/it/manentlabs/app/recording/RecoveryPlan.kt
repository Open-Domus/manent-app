// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

/**
 * How to close a recording the process died in the middle of, from the part
 * files left on disk. At most the part being written is lost.
 */
data class RecoveryPlan(
    val parts: List<Part>,
    val chunkCount: Int,
    val endedAtMs: Long,
    /** Nothing was recorded and the gateway never saw it: delete instead of closing. */
    val discard: Boolean,
) {
    /** [interrupted] for audio cut off by the process dying, whether or not it could be kept. */
    data class Part(val seq: Int, val startedAtMs: Long, val durationMs: Long, val silent: Boolean, val interrupted: Boolean)

    /**
     * A part file found on disk. [playable] is false for one that cannot be
     * decoded, typically the part being written when the process died, which
     * never got its index.
     */
    data class DiskPart(val sizeBytes: Long, val modifiedAtMs: Long, val playable: Boolean = true)

    companion object {
        /**
         * Files every part on disk past the [filedCount] already filed. Their
         * lengths are estimated from size at 32 kbit/s and they end at their
         * file's modification time, never before the previous part ends. A
         * missing or empty file in the sequence becomes a silent 0 ms part, so
         * numbering stays dense. An unplayable file keeps its estimated length
         * but is filed silent, so the gateway is never sent audio it cannot read.
         */
        fun of(session: OutboxSession, files: Map<Int, DiskPart>): RecoveryPlan {
            val last = files.keys.filter { it >= session.filedCount }.maxOrNull() ?: (session.filedCount - 1)
            var previousEnd = session.lastFiledEndMs ?: session.startedAtMs
            val parts = (session.filedCount..last).map { seq ->
                val file = files[seq]?.takeIf { it.sizeBytes > 0 }
                if (file == null) {
                    Part(seq, previousEnd, 0, silent = true, interrupted = false)
                } else {
                    val duration = file.sizeBytes * 1000 / RecordingLimits.BYTES_PER_SECOND
                    val start = maxOf(file.modifiedAtMs - duration, previousEnd)
                    previousEnd = start + duration
                    Part(seq, start, duration, silent = !file.playable, interrupted = true)
                }
            }
            val end = parts.lastOrNull()?.let { it.startedAtMs + it.durationMs } ?: session.lastFiledEndMs ?: session.startedAtMs
            val recordedNothing = session.filedCount == 0 && parts.all { it.silent }
            return RecoveryPlan(parts, session.filedCount + parts.size, end, discard = recordedNothing && !session.created)
        }
    }
}
