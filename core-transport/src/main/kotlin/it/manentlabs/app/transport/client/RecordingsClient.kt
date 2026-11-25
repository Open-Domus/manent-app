// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Lorenzo Farnararo

package it.manentlabs.app.transport.client

import it.manentlabs.app.transport.dto.FinishRecordingBody
import it.manentlabs.app.transport.dto.RecordingChunkAckDto
import it.manentlabs.app.transport.dto.RecordingChunkMetadata
import it.manentlabs.app.transport.dto.RecordingSpeakerDto
import it.manentlabs.app.transport.dto.RecordingSpeakersResponse
import it.manentlabs.app.transport.dto.RecordingStatusDto
import it.manentlabs.app.transport.dto.RecordingSummaryDto
import it.manentlabs.app.transport.dto.RecordingsListResponse
import it.manentlabs.app.transport.dto.SpeakerMapping
import it.manentlabs.app.transport.dto.StartRecordingBody
import it.manentlabs.app.transport.http.GatewayHttp
import it.manentlabs.app.transport.dto.OmnesisJson
import it.manentlabs.app.transport.http.deleteSegments
import it.manentlabs.app.transport.http.getPrivateJson
import it.manentlabs.app.transport.http.postJson
import it.manentlabs.app.transport.http.postJsonSegments
import it.manentlabs.app.transport.http.putBodySegments
import it.manentlabs.app.transport.http.putJsonSegments
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.time.Duration
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

/**
 * The gateway's recorded-conversation routes (experimental; a gateway without
 * them answers 404). Every call is idempotent, so an outbox may repeat any of
 * them after a lost reply.
 */
class RecordingsClient(private val http: GatewayHttp) {
    /** `POST /recordings` — 201 for a new session, 200 for a retry. 409 `DICTATION_DISABLED` when transcription is off. */
    suspend fun start(body: StartRecordingBody): RecordingStatusDto = http.postJson("recordings", body)

    /**
     * `PUT /recordings/:id/chunks/:seq` — one chunk; [audio] is null for a silent one.
     * 404 when the session is unknown, 409 when [seq] lies past a finished session's count.
     */
    suspend fun putChunk(
        id: String,
        seq: Int,
        metadata: RecordingChunkMetadata,
        audio: File?,
        audioMimeType: String?,
    ): RecordingChunkAckDto =
        http.putBodySegments(
            listOf("recordings", id, "chunks", seq.toString()),
            chunkBody(metadata, audio, audioMimeType),
            CHUNK_UPLOAD_TIMEOUT,
        )

    /** `POST /recordings/:id/finish` — idempotent; 409 when a different count was declared. */
    suspend fun finish(id: String, body: FinishRecordingBody): RecordingStatusDto =
        http.postJsonSegments(listOf("recordings", id, "finish"), body)

    suspend fun status(id: String): RecordingStatusDto = http.getPrivateJson(listOf("recordings", id))

    /** `DELETE /recordings/:id` — the session, its chunks and its document. */
    suspend fun delete(id: String) = http.deleteSegments(listOf("recordings", id))

    /** `GET /recordings?limit=` — the gateway's most recent recordings, newest first. */
    suspend fun list(limit: Int = 20): List<RecordingSummaryDto> =
        http.getPrivateJson<RecordingsListResponse>(listOf("recordings"), mapOf("limit" to limit.toString()))
            .recordings

    /**
     * `GET /recordings/:id/speakers` — who spoke, with how long and a few lines each.
     * Empty when the gateway did not tell the speakers apart; 404 for an unknown recording.
     */
    suspend fun speakers(id: String): List<RecordingSpeakerDto> =
        http.getPrivateJson<RecordingSpeakersResponse>(listOf("recordings", id, "speakers")).speakers

    /**
     * `PUT /recordings/:id/speakers/:n` — say who speaker [speaker] is; answers the
     * recording's speakers as they now stand. 404 for an unknown speaker, 400 for an
     * unknown person id.
     */
    suspend fun setSpeaker(id: String, speaker: Int, mapping: SpeakerMapping): List<RecordingSpeakerDto> =
        http.putJsonSegments<JsonObject, RecordingSpeakersResponse>(
            listOf("recordings", id, "speakers", speaker.toString()),
            mapping.toJson(),
        ).speakers

    companion object {
        /** A chunk is about a minute of compressed audio; a slow uplink still gets it through. */
        val CHUNK_UPLOAD_TIMEOUT: Duration = Duration.ofMinutes(2)

        fun chunkBody(metadata: RecordingChunkMetadata, audio: File?, audioMimeType: String?): MultipartBody =
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chunk", OmnesisJson.encodeToString(metadata))
                .apply {
                    if (audio != null) {
                        val type = (audioMimeType ?: "application/octet-stream").toMediaType()
                        addFormDataPart("audio", audio.name, audio.asRequestBody(type))
                    }
                }
                .build()
    }
}
