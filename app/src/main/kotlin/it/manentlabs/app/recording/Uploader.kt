// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

import android.util.Log
import it.manentlabs.app.gateway.ChunkUpload
import it.manentlabs.app.gateway.Gateway
import it.manentlabs.app.gateway.GatewayApi
import it.manentlabs.app.gateway.GatewayError
import it.manentlabs.app.gateway.RecordingStart
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends the outbox to the gateway, oldest recording first: open it once, send
 * each part and forget it locally when acknowledged, then close it with its
 * final count and drop it from the phone: the outbox is only a queue. Every call is idempotent on the gateway, so a pass cut short
 * is simply repeated.
 */
@Singleton
class Uploader @Inject constructor(
    private val gateway: Gateway,
    private val outbox: Outbox,
    private val storage: RecordingStorage,
) {
    enum class Pass {
        /** Something was sent: there may be more. */
        SENT,

        /** Nothing left to send. */
        IDLE,

        /** Something could not be sent now: try again later. */
        RETRY,
    }

    suspend fun drain(): Pass {
        val api = gateway.api.value ?: return Pass.RETRY
        if (gateway.refreshStatus()?.acceptsRecordings != true) return Pass.RETRY
        var sent = false
        var retry = false
        for (session in outbox.sessions().filter(DrainPolicy::shouldSend)) {
            try {
                if (send(api, session)) sent = true
            } catch (e: GatewayError) {
                Log.w(TAG, "Upload of a recording failed: ${e::class.simpleName} ${e.message}")
                when (val action = DrainPolicy.onFailure(e)) {
                    DrainPolicy.FailureAction.Reopen -> {
                        outbox.setCreated(session.id, false)
                        retry = true
                    }
                    DrainPolicy.FailureAction.Retry -> retry = true
                    is DrainPolicy.FailureAction.Refuse -> outbox.refuse(session.id, action.reason)
                }
            }
        }
        return when {
            retry -> Pass.RETRY
            sent -> Pass.SENT
            else -> Pass.IDLE
        }
    }

    /** Sends what one recording has pending; true when anything went out. */
    private suspend fun send(api: GatewayApi, session: OutboxSession): Boolean {
        var sent = false
        if (!session.created) {
            api.startRecording(
                RecordingStart(
                    id = session.id,
                    startedAt = Instant.ofEpochMilli(session.startedAtMs),
                    title = session.title,
                    ambient = session.ambient,
                    timeZoneId = session.zoneId,
                    utcOffsetSeconds = session.utcOffsetSeconds,
                    language = session.language,
                ),
            )
            outbox.setCreated(session.id, true)
            sent = true
        }
        for (chunk in outbox.chunks(session.id)) {
            api.uploadChunk(
                session.id,
                chunk.seq,
                ChunkUpload(
                    startedAt = Instant.ofEpochMilli(chunk.startedAtMs),
                    durationMs = chunk.durationMs,
                    silent = chunk.silent,
                    interrupted = chunk.interrupted,
                    audio = chunk.path?.let(::File),
                    mimeType = chunk.mimeType,
                ),
            )
            outbox.removeChunk(session.id, chunk.seq)
            chunk.path?.let { File(it).delete() }
            sent = true
        }
        if (DrainPolicy.readyToFinish(session, outbox.chunks(session.id).size)) {
            api.finishRecording(session.id, session.chunkCount!!, Instant.ofEpochMilli(session.endedAtMs ?: session.startedAtMs))
            outbox.deleteSession(session.id)
            storage.deleteSession(session.id)
            sent = true
        }
        return sent
    }

    private companion object {
        const val TAG = "Manent:Upload"
    }
}
