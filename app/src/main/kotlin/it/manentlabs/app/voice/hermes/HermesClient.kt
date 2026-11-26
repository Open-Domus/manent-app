// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.hermes

import it.manentlabs.app.voice.CommandOutcome
import it.manentlabs.app.voice.webhook.WebhookUrl
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** What "Save and test" found on a Hermes Agent API server. */
sealed interface HermesCheck {
    /** [model] is the name the server advertises, usually `hermes-agent` or the profile's. */
    data class Ok(val model: String?) : HermesCheck

    data object BadKey : HermesCheck

    /** [reason] is short and technical, shown as is. */
    data class Unreachable(val reason: String) : HermesCheck
}

/**
 * A client of Hermes Agent's OpenAI-compatible API server (`API_SERVER_ENABLED=true`
 * in `~/.hermes/.env`, port 8642). The command goes to `/v1/chat/completions`;
 * the agent may run tools before it answers, so the call waits up to
 * [ASK_TIMEOUT_S]. Every command carries the same session key, so the agent's
 * long-term memory sees one "Manent" channel. Never retried: a retry could run
 * the agent's tools twice.
 */
class HermesClient(http: OkHttpClient) {
    private val asking = http.newBuilder().callTimeout(ASK_TIMEOUT_S, TimeUnit.SECONDS).readTimeout(ASK_TIMEOUT_S, TimeUnit.SECONDS).build()
    private val testing = http.newBuilder().callTimeout(TEST_TIMEOUT_S, TimeUnit.SECONDS).build()

    /** Lists the models, which needs the key: proves address and key. Blocking. */
    fun test(address: String, key: String): HermesCheck {
        val url = endpoint(address, "models") ?: return HermesCheck.Unreachable("bad address")
        val request = Request.Builder().url(url).get().header("Authorization", "Bearer $key").build()
        return try {
            testing.newCall(request).execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 -> HermesCheck.BadKey
                    !response.isSuccessful -> HermesCheck.Unreachable("HTTP ${response.code}")
                    else -> {
                        val body = parse(response.body?.string()) ?: return HermesCheck.Unreachable("not Hermes")
                        val first = (body["data"] as? JsonArray)?.firstOrNull() as? JsonObject
                        HermesCheck.Ok(first?.string("id"))
                    }
                }
            }
        } catch (_: IOException) {
            HermesCheck.Unreachable("unreachable")
        }
    }

    /** Sends [text] to the agent; its reply is to be read aloud. Blocking. */
    fun ask(address: String, key: String, text: String, locale: String): CommandOutcome {
        val url = endpoint(address, "chat/completions") ?: return CommandOutcome.Failed("bad Hermes address")
        val request = Request.Builder()
            .url(url)
            .post(body(text, locale).toRequestBody(JSON))
            .header("Authorization", "Bearer $key")
            .header("X-Hermes-Session-Key", SESSION_KEY)
            .build()
        return try {
            asking.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return CommandOutcome.Failed("HTTP ${response.code}")
                outcome(response.body?.string())
            }
        } catch (_: IOException) {
            CommandOutcome.Failed("unreachable")
        }
    }

    companion object {
        const val MODEL = "hermes-agent"
        const val SESSION_KEY = "manent-voice"
        private const val ASK_TIMEOUT_S = 90L
        private const val TEST_TIMEOUT_S = 10L
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * `http://host:8642` (with or without `/v1`, or a profile's `/p/name`)
         * → `http://host:8642/v1/<path>`; null when it is not an http(s) address.
         */
        fun endpoint(address: String, path: String): HttpUrl? {
            val text = address.trim().trimEnd('/')
            if (!WebhookUrl.isValid(text)) return null
            val base = text.removeSuffix("/v1")
            return "$base/v1/$path".toHttpUrlOrNull()
        }

        /** The request: a short instruction that the reply will be heard, not read, then the command. */
        fun body(text: String, locale: String): String = buildJsonObject {
            put("model", MODEL)
            put("stream", false)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", instructions(locale))
                }
                addJsonObject {
                    put("role", "user")
                    put("content", text)
                }
            }
        }.toString()

        fun instructions(locale: String): String =
            "The user spoke this command to a wearable; the phone transcribed it and will read your reply aloud. " +
                "Answer in one or two short spoken sentences in the user's language (locale $locale), " +
                "with no markdown, lists, links or emoji. The transcript may contain recognition errors."

        /** The reply's text as an answer; a reply with nothing to say is still done. */
        fun outcome(body: String?): CommandOutcome {
            val reply = parse(body) ?: return CommandOutcome.Failed("not a chat completion")
            val choice = (reply["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
                ?: return CommandOutcome.Failed("no choices")
            return when (val finish = choice.string("finish_reason")) {
                "error", "cancelled" -> CommandOutcome.Failed("agent $finish")
                else -> {
                    val content = (choice["message"] as? JsonObject)?.string("content")?.trim()
                    if (finish == "length" && content.isNullOrEmpty()) {
                        CommandOutcome.Failed("agent ran out of steps")
                    } else {
                        CommandOutcome.Done(speech = content?.takeIf { it.isNotEmpty() }, answered = true)
                    }
                }
            }
        }

        private fun parse(body: String?): JsonObject? = try {
            body?.let { json.parseToJsonElement(it) as? JsonObject }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    }
}
