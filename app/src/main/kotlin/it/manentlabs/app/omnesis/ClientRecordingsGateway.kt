// SPDX-License-Identifier: MIT
// Copied from Omnesis android/app/.../di/RecordingSessionModule.kt (ClientRecordingsGateway).

package it.manentlabs.app.omnesis

import dev.omnesis.android.feature.recording.OutboxChunk
import dev.omnesis.android.feature.recording.OutboxSession
import dev.omnesis.android.feature.recording.RecordingsGateway
import dev.omnesis.android.transport.client.GatewayClient
import dev.omnesis.android.transport.client.RecordingsClient
import dev.omnesis.android.transport.client.SearchClient
import dev.omnesis.android.transport.dto.FinishRecordingBody
import dev.omnesis.android.transport.dto.PersonSummary
import dev.omnesis.android.transport.dto.RecordingChunkMetadata
import dev.omnesis.android.transport.dto.RecordingSpeakerDto
import dev.omnesis.android.transport.dto.RecordingSummaryDto
import dev.omnesis.android.transport.dto.SpeakerMapping
import dev.omnesis.android.transport.dto.StartRecordingBody

/**
 * [RecordingsGateway] over the session's [RecordingsClient], with [GatewayClient] for the
 * capability check and [SearchClient] for choosing who a speaker is.
 */
class ClientRecordingsGateway(
    private val client: RecordingsClient,
    private val gateway: GatewayClient,
    private val search: SearchClient,
    private val deviceId: String?,
) : RecordingsGateway {
    override suspend fun accepting(): Boolean = gateway.status().let { it.experimental && it.recordings }

    override suspend fun start(session: OutboxSession) {
        client.start(
            StartRecordingBody(
                id = session.id,
                title = session.title,
                origin = if (session.ambient) "ambient" else null,
                startedAt = session.startedAt,
                startedTimeZoneId = session.startedTimeZoneId,
                startedUtcOffsetSeconds = session.startedUtcOffsetSeconds,
                language = session.language,
                deviceId = deviceId,
            ),
        )
    }

    override suspend fun putChunk(chunk: OutboxChunk) {
        client.putChunk(
            id = chunk.sessionId,
            seq = chunk.seq,
            metadata = RecordingChunkMetadata(
                startedAt = chunk.startedAt,
                durationMs = chunk.durationMs,
                silent = chunk.silent.takeIf { it },
                interrupted = chunk.interrupted.takeIf { it },
            ),
            audio = if (chunk.silent) null else chunk.file,
            audioMimeType = chunk.mimeType,
        )
    }

    override suspend fun finish(sessionId: String, chunkCount: Int, endedAt: String) {
        client.finish(sessionId, FinishRecordingBody(chunkCount, endedAt))
    }

    override suspend fun delete(sessionId: String) = client.delete(sessionId)

    override suspend fun recent(limit: Int): List<RecordingSummaryDto> = client.list(limit)

    override suspend fun speakers(sessionId: String): List<RecordingSpeakerDto> = client.speakers(sessionId)

    override suspend fun setSpeaker(sessionId: String, speaker: Int, mapping: SpeakerMapping): List<RecordingSpeakerDto> =
        client.setSpeaker(sessionId, speaker, mapping)

    override suspend fun searchPeople(query: String, limit: Int): List<PersonSummary> =
        search.people(query.ifBlank { null }, limit)
}

