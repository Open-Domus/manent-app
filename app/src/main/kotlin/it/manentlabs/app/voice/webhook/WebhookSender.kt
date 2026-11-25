// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.webhook

import it.manentlabs.app.voice.CommandOutcome
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Posts a transcribed command to the owner's webhook. Any 2xx counts as
 * done; the reply's body is not read, so nothing is spoken. Never retried.
 */
class WebhookSender(http: OkHttpClient) {
    private val client = http.newBuilder().callTimeout(CALL_TIMEOUT_S, TimeUnit.SECONDS).build()

    /** Blocking; call off the main thread. */
    fun send(url: String, secret: String?, text: String, wakeWord: String, locale: String, at: Instant = Instant.now()): CommandOutcome {
        val address = url.trim()
        if (address.isEmpty()) return CommandOutcome.Failed("no webhook address")
        val parsed = address.takeIf(WebhookUrl::isValid)?.toHttpUrlOrNull() ?: return CommandOutcome.Failed("bad webhook address")
        val request = Request.Builder()
            .url(parsed)
            .post(body(text, wakeWord, locale, at).toRequestBody(JSON))
            .apply { if (!secret.isNullOrBlank()) header("Authorization", "Bearer $secret") }
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) CommandOutcome.Done() else CommandOutcome.Failed("HTTP ${response.code}")
            }
        } catch (_: IOException) {
            CommandOutcome.Failed("unreachable")
        }
    }

    companion object {
        private const val CALL_TIMEOUT_S = 10L
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** The JSON posted: every key always present. */
        fun body(text: String, wakeWord: String, locale: String, at: Instant): String = buildJsonObject {
            put("text", text)
            put("wakeWord", wakeWord)
            put("locale", locale)
            put("at", at.truncatedTo(ChronoUnit.MILLIS).toString())
        }.toString()
    }
}
