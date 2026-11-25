// SPDX-License-Identifier: MIT

package it.manentlabs.app.omnesis

import it.manentlabs.app.transport.client.NotesClient
import it.manentlabs.app.transport.http.GatewayHttp
import it.manentlabs.app.transport.http.postBody
import java.io.File
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody

/**
 * One-shot transcription on the gateway, keeping nothing.
 *
 * Calls `POST /inference/transcribe`, which any `write:*` scope may use, rather
 * than `NotesClient.transcribeCapture`: that one tries the Watch capture route
 * first, which needs `admin`, and this device holds only narrow scopes.
 */
class GatewayTranscriber(private val http: GatewayHttp) {
    suspend fun transcribe(audio: File, mimeType: String, locale: String?): String? {
        val query = NotesClient.transcriptionLanguage(locale)?.let { mapOf("language" to it) }.orEmpty()
        val response: TranscriptResponse = http.postBody(
            PATH,
            audio.asRequestBody(mimeType.toMediaType()),
            NotesClient.VOICE_UPLOAD_TIMEOUT,
            query,
        )
        return response.text?.takeIf { response.available }
    }

    @Serializable
    internal data class TranscriptResponse(val available: Boolean, val text: String? = null)

    companion object {
        const val PATH = "inference/transcribe"
    }
}
