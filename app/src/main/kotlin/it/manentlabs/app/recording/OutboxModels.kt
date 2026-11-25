// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

/** A recording as the outbox keeps it, from its start until the gateway has all of it. */
data class OutboxSession(
    val id: String,
    val title: String?,
    val startedAtMs: Long,
    val zoneId: String,
    val utcOffsetSeconds: Int,
    val language: String,
    /** An all-day episode rather than a recording someone started. */
    val ambient: Boolean,
    /** Set once stopped. */
    val chunkCount: Int? = null,
    val endedAtMs: Long? = null,
    /** The gateway has been told about it. */
    val created: Boolean = false,
    /** Why the gateway refused it for good; nothing more is sent. */
    val refusal: String? = null,
    /** Parts filed so far, sent or not. */
    val filedCount: Int = 0,
    val lastFiledEndMs: Long? = null,
    /** At least one part kept its audio. */
    val hasAudio: Boolean = false,
) {
    val stopped: Boolean get() = chunkCount != null
}

/** A filed part that the gateway has not acknowledged yet. */
data class OutboxChunk(
    val sessionId: String,
    val seq: Int,
    val startedAtMs: Long,
    val durationMs: Long,
    val silent: Boolean,
    val interrupted: Boolean,
    /** The audio file; null for a silent part. */
    val path: String?,
    val mimeType: String?,
)

/** A recording for the "not sent yet" list. */
data class LocalRecording(val session: OutboxSession, val unsentParts: Int)
