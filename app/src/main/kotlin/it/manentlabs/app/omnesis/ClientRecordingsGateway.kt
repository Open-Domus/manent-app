// SPDX-License-Identifier: MIT

package it.manentlabs.app.omnesis

import it.manentlabs.app.feature.recording.OutboxChunk
import it.manentlabs.app.feature.recording.OutboxSession
import it.manentlabs.app.feature.recording.RecordingsGateway
import it.manentlabs.app.transport.client.GatewayClient
import it.manentlabs.app.transport.client.RecordingsClient
import it.manentlabs.app.transport.client.SearchClient
import it.manentlabs.app.transport.dto.FinishRecordingBody
import it.manentlabs.app.transport.dto.PersonSummary
import it.manentlabs.app.transport.dto.RecordingChunkMetadata
import it.manentlabs.app.transport.dto.RecordingSpeakerDto
import it.manentlabs.app.transport.dto.RecordingSummaryDto
import it.manentlabs.app.transport.dto.SpeakerMapping
import it.manentlabs.app.transport.dto.StartRecordingBody

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

