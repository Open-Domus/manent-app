// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

/**
 * The gateway endpoints the app calls, bound to one pairing. Every call
 * suspends, makes exactly one request and throws [GatewayError] on failure;
 * retrying is the caller's business.
 */
class GatewayApi internal constructor(
    private val base: HttpUrl,
    private val token: String,
    private val generation: String?,
    /** The paired device id, sent with new recordings and voice notes. */
    val deviceId: String,
    private val clients: GatewayClients,
) {
    /** E1: what the gateway currently offers. */
    suspend fun status(): GatewayStatus =
        clients.standard.exchange(get("status").build(), GatewayStatus::parse)

    /** E2: opens a recording session, or confirms one already open with the same id. */
    suspend fun startRecording(start: RecordingStart) {
        val body = RecordingStartBody(
            id = start.id,
            title = start.title,
            origin = if (start.ambient) "ambient" else null,
            startedAt = start.startedAt.toString(),
            startedTimeZoneId = start.timeZoneId,
            startedUtcOffsetSeconds = start.utcOffsetSeconds,
            language = start.language,
            deviceId = deviceId,
        )
        clients.standard.exchange(post(json(body), "recordings").build()) {}
    }

    /** E3: sends part [seq] (0-based) of recording [recordingId]. */
    suspend fun uploadChunk(recordingId: String, seq: Int, chunk: ChunkUpload) {
        val audio = chunk.audio?.takeUnless { chunk.silent }
        val missing = audio != null && !audio.isFile
        val meta = ChunkBody(
            startedAt = chunk.startedAt.toString(),
            durationMs = chunk.durationMs,
            silent = (chunk.silent || missing).takeIf { it },
            interrupted = (chunk.interrupted || missing).takeIf { it },
        )
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chunk", GatewayJson.encodeToString(ChunkBody.serializer(), meta))
        if (audio != null && !missing) {
            val type = chunk.mimeType?.toMediaTypeOrNull() ?: OCTET_STREAM
            form.addFormDataPart("audio", audio.name, audio.asRequestBody(type))
        }
        val request = request("recordings", recordingId, "chunks", seq.toString()).put(form.build()).build()
        clients.upload.exchange(request) {}
    }

    /** E4: closes a session with its final part count. */
    suspend fun finishRecording(recordingId: String, chunkCount: Int, endedAt: Instant) {
        val body = json(FinishBody(chunkCount, endedAt.toString()))
        clients.standard.exchange(post(body, "recordings", recordingId, "finish").build()) {}
    }

    /** E5: deletes a session with its parts and document. A [GatewayError.NotFound] means it is already gone. */
    suspend fun deleteRecording(recordingId: String) {
        clients.standard.exchange(request("recordings", recordingId).delete().build()) {}
    }

    /** E6: the gateway's recent recordings, newest first. */
    suspend fun recentRecordings(limit: Int = 20): List<GatewayRecording> {
        val request = Request.Builder().url(url("recordings").addQueryParameter("limit", limit.toString()).build())
            .authorized().header("Cache-Control", "no-store").build()
        return clients.standard.exchange(request) { GatewayJson.decodeFromString(RecordingList.serializer(), it).recordings }
    }

    /** E7: who spoke in a recording; empty when the gateway could not tell voices apart. */
    suspend fun speakers(recordingId: String): List<RecordingSpeaker> {
        val request = get("recordings", recordingId, "speakers").header("Cache-Control", "no-store").build()
        return clients.standard.exchange(request, ::decodeSpeakers)
    }

    /** E8: says who speaker [speaker] is; the answer is the recording's updated speaker list. */
    suspend fun assignSpeaker(recordingId: String, speaker: Int, assignment: SpeakerAssignment): List<RecordingSpeaker> {
        val body = buildJsonObject {
            when (assignment) {
                SpeakerAssignment.Me -> put("self", true)
                is SpeakerAssignment.Person -> put("personId", assignment.personId)
                is SpeakerAssignment.Name -> put("name", assignment.name)
                SpeakerAssignment.Clear -> put("clear", true)
            }
        }.toString().toRequestBody(JSON)
        val request = request("recordings", recordingId, "speakers", speaker.toString()).put(body).build()
        return clients.standard.exchange(request, ::decodeSpeakers)
    }

    /** E9: people whose name matches [query]; a blank query lists people without filtering. */
    suspend fun searchPeople(query: String, limit: Int = 20): List<GatewayPerson> {
        val url = url("people").apply {
            query.trim().takeIf { it.isNotEmpty() }?.let { addQueryParameter("q", it) }
            addQueryParameter("limit", limit.toString())
        }.build()
        val request = Request.Builder().url(url).authorized().build()
        return clients.standard.exchange(request) { GatewayJson.decodeFromString(PeoplePage.serializer(), it).items }
    }

    /** E10: saves a marked moment's clip as a voice note, which the gateway transcribes later. */
    suspend fun sendVoiceNote(
        note: VoiceNoteUpload,
        locale: Locale = Locale.getDefault(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        val meta = VoiceNoteBody(
            id = UUID.randomUUID().toString(),
            text = "",
            capturedAt = note.capturedAt.toString(),
            surface = "android-marked-moment",
            deviceId = deviceId,
            language = locale.toLanguageTag(),
            capturedTimeZoneId = zone.id,
            capturedUtcOffsetSeconds = zone.rules.getOffset(note.capturedAt).totalSeconds,
            recordingId = note.recordingId,
        )
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("note", GatewayJson.encodeToString(VoiceNoteBody.serializer(), meta))
            .addFormDataPart("audio", note.audio.name, note.audio.asRequestBody(AUDIO_MP4))
            .build()
        clients.long.exchange(post(form, "notes", "voice").build()) {}
    }

    /**
     * E11: transcribes a short WAV and keeps nothing. Returns null when the
     * gateway has no transcriber to offer.
     */
    suspend fun transcribe(wav: File, localeTag: String?): String? {
        val url = url("inference", "transcribe").apply {
            TranscriptionLanguage.of(localeTag)?.let { addQueryParameter("language", it) }
        }.build()
        val request = Request.Builder().url(url).authorized().post(wav.asRequestBody(AUDIO_WAV)).build()
        return clients.long.exchange(request) {
            val reply = GatewayJson.decodeFromString(TranscriptionReply.serializer(), it)
            reply.text.takeIf { reply.available }
        }
    }

    private fun decodeSpeakers(body: String) = GatewayJson.decodeFromString(SpeakerList.serializer(), body).speakers

    private inline fun <reified T> json(value: T): RequestBody =
        GatewayJson.encodeToString(kotlinx.serialization.serializer<T>(), value).toRequestBody(JSON)

    private fun url(vararg segments: String): HttpUrl.Builder =
        base.newBuilder().apply { segments.forEach { addPathSegment(it) } }

    private fun request(vararg segments: String) = Request.Builder().url(url(*segments).build()).authorized()

    private fun get(vararg segments: String) = request(*segments).get()

    private fun post(body: RequestBody, vararg segments: String) = request(*segments).post(body)

    private fun Request.Builder.authorized() = apply {
        header("Accept", "application/json")
        header("Authorization", "Bearer $token")
        generation?.let { header("Omnesis-Pairing-Generation", it) }
    }

    override fun toString() = "GatewayApi($base)"

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        val OCTET_STREAM = "application/octet-stream".toMediaType()
        val AUDIO_MP4 = "audio/mp4".toMediaType()
        val AUDIO_WAV = "audio/wav".toMediaType()
    }
}

/** The per-pairing HTTP clients: one per timeout profile, all sharing trust and connections. */
internal class GatewayClients(
    val standard: OkHttpClient,
    /** Part uploads: 2 minutes. */
    val upload: OkHttpClient,
    /** Voice notes and transcription: 3 minutes. */
    val long: OkHttpClient,
)
