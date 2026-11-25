// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.homeassistant

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** An Assist pipeline (an "assistant" in Home Assistant's settings). */
data class Pipeline(val id: String, val name: String, val language: String?)

/** What "Save and test" found. */
sealed interface ConnectionCheck {
    data class Ok(val version: String?, val pipelines: List<Pipeline>, val preferred: String?) : ConnectionCheck

    data object BadToken : ConnectionCheck

    /** [reason] is short and technical, shown as is: "unreachable", "timeout", an error code… */
    data class Unreachable(val reason: String) : ConnectionCheck
}

/** Signs in and lists the Assist pipelines, to prove address and token work. */
class ConnectionTest(token: String) : HaConversation<ConnectionCheck>(token) {
    override fun request(): JsonObject = buildJsonObject {
        put("id", REQUEST_ID)
        put("type", "assist_pipeline/pipeline/list")
    }

    override fun onReply(message: JsonObject): List<Step<ConnectionCheck>> {
        if (message.string("type") != "result") return emptyList()
        if ((message["success"] as? JsonPrimitive)?.contentOrNull != "true") {
            return finish(ConnectionCheck.Unreachable(message.obj("error")?.string("code") ?: "error"))
        }
        val result = message.obj("result")
        val pipelines = (result?.get("pipelines") as? JsonArray).orEmpty().mapNotNull { item ->
            val pipeline = item as? JsonObject ?: return@mapNotNull null
            val id = pipeline.string("id") ?: return@mapNotNull null
            Pipeline(id, pipeline.string("name") ?: id, pipeline.string("language"))
        }
        if (pipelines.isEmpty()) return finish(ConnectionCheck.Unreachable("no pipelines"))
        return finish(ConnectionCheck.Ok(version, pipelines, result?.string("preferred_pipeline")))
    }

    private fun finish(check: ConnectionCheck) = listOf(Step.Finish(check))

    override fun notHomeAssistant() = ConnectionCheck.Unreachable("not Home Assistant")
    override fun badToken() = ConnectionCheck.BadToken
    override fun unexpected(type: String) = ConnectionCheck.Unreachable("unexpected $type")
    override fun unreachable() = ConnectionCheck.Unreachable("unreachable")
    override fun closed() = ConnectionCheck.Unreachable("closed")
    override fun timedOut(stage: String) = ConnectionCheck.Unreachable("timeout")
}
