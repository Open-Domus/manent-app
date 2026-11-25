// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.homeassistant

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * One exchange over Home Assistant's WebSocket API, as pure logic: it is fed
 * each text message and answers with what to do next. Signing in is the same
 * for every exchange: the server says `auth_required`, the token goes back,
 * and `auth_ok` lets the exchange's own request go out as message 1. Only
 * messages with id 1 reach the exchange; a frame may hold a JSON array of messages.
 */
abstract class HaConversation<T>(private val token: String) {
    /** What the socket should do. */
    sealed interface Step<out T> {
        data class Send(val text: String) : Step<Nothing>

        /** Stream the command audio to the given binary handler. */
        data class Audio(val handler: Int) : Step<Nothing>

        data class Finish<T>(val result: T) : Step<T>
    }

    private var greeted = false
    private var signedIn = false

    /** The last stage reached, for a timeout's reason: "sign-in", then each event type seen. */
    var stage: String = "sign-in"
        protected set

    /** Home Assistant's version, once it has said. */
    var version: String? = null
        private set

    fun onText(text: String): List<Step<T>> {
        val element = try {
            Json.parseToJsonElement(text)
        } catch (_: IllegalArgumentException) {
            return if (greeted) emptyList() else listOf(Step.Finish(notHomeAssistant()))
        }
        val messages = (element as? JsonArray)?.toList() ?: listOf(element)
        val steps = mutableListOf<Step<T>>()
        for (message in messages) {
            if (message !is JsonObject) {
                if (!greeted) return listOf(Step.Finish(notHomeAssistant()))
                continue
            }
            steps += handle(message)
            if (steps.any { it is Step.Finish }) break
        }
        return steps
    }

    /** The socket closed or failed before an answer. */
    fun onClosed(): T = if (greeted) closed() else unreachable()

    /** No answer in time. */
    fun onTimeout(): T = timedOut(stage)

    private fun handle(message: JsonObject): List<Step<T>> {
        val type = message.string("type")
        if (!greeted) {
            if (type != "auth_required") return listOf(Step.Finish(notHomeAssistant()))
            greeted = true
            version = message.string("ha_version")
            val auth = buildJsonObject {
                put("type", "auth")
                put("access_token", token)
            }
            return listOf(Step.Send(auth.toString()))
        }
        if (!signedIn) {
            return when (type) {
                "auth_ok" -> {
                    signedIn = true
                    message.string("ha_version")?.let { version = it }
                    listOf(Step.Send(request().toString()))
                }
                "auth_invalid" -> listOf(Step.Finish(badToken()))
                else -> listOf(Step.Finish(unexpected(type ?: "message")))
            }
        }
        if ((message["id"] as? JsonPrimitive)?.intOrNull != REQUEST_ID) return emptyList()
        return onReply(message)
    }

    /** The request sent once signed in, with id 1. */
    protected abstract fun request(): JsonObject

    /** A message answering the request. */
    protected abstract fun onReply(message: JsonObject): List<Step<T>>

    protected abstract fun notHomeAssistant(): T
    protected abstract fun badToken(): T
    protected abstract fun unexpected(type: String): T
    protected abstract fun unreachable(): T
    protected abstract fun closed(): T
    protected abstract fun timedOut(stage: String): T

    protected companion object {
        const val REQUEST_ID = 1

        fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

        fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

        fun JsonElement?.path(vararg keys: String): JsonElement? {
            var at: JsonElement? = this
            for (key in keys) at = (at as? JsonObject)?.get(key)
            return at
        }
    }
}
