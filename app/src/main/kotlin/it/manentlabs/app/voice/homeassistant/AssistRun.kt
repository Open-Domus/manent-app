// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.homeassistant

import it.manentlabs.app.audio.PcmFormat
import it.manentlabs.app.voice.CommandOutcome
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * One command through an Assist pipeline, from speech to intent: Home
 * Assistant transcribes the audio and acts on it, no text-to-speech asked
 * for. The audio goes once, when the run hands out its binary handler.
 */
class AssistRun(token: String, private val pipeline: String?) : HaConversation<CommandOutcome>(token) {
    private var streamed = false

    override fun request(): JsonObject = buildJsonObject {
        put("id", REQUEST_ID)
        put("type", "assist_pipeline/run")
        put("start_stage", "stt")
        put("end_stage", "intent")
        putJsonObject("input") { put("sample_rate", PcmFormat.SAMPLE_RATE) }
        if (!pipeline.isNullOrBlank()) put("pipeline", pipeline)
    }

    override fun onReply(message: JsonObject): List<Step<CommandOutcome>> = when (message.string("type")) {
        "result" -> if ((message["success"] as? JsonPrimitive)?.contentOrNull == "true") {
            emptyList()
        } else {
            val error = message.obj("error")
            finish(failed(error?.string("code") ?: error?.string("message") ?: "run refused"))
        }
        "event" -> onEvent(message.obj("event"))
        else -> emptyList()
    }

    private fun onEvent(event: JsonObject?): List<Step<CommandOutcome>> {
        val type = event?.string("type") ?: return emptyList()
        stage = type
        val data = event.obj("data")
        return when (type) {
            "run-start" -> {
                val handler = (data.path("runner_data", "stt_binary_handler_id") as? JsonPrimitive)?.intOrNull
                when {
                    handler == null -> finish(failed("no audio handler"))
                    streamed -> emptyList()
                    else -> {
                        streamed = true
                        listOf(Step.Audio(handler))
                    }
                }
            }
            "intent-end" -> finish(answer(data.path("intent_output", "response") as? JsonObject))
            "error" -> {
                val code = data?.string("code") ?: "error"
                finish(if (code in NOT_UNDERSTOOD) CommandOutcome.NotUnderstood(code) else failed(code))
            }
            "run-end" -> finish(failed("ended without an answer"))
            else -> emptyList()
        }
    }

    private fun answer(response: JsonObject?): CommandOutcome {
        if (response == null) return failed("no response")
        val speech = (response.path("speech", "plain", "speech") as? JsonPrimitive)?.contentOrNull
        return when (response.string("response_type")) {
            "action_done" -> CommandOutcome.Done(speech)
            "query_answer" -> CommandOutcome.Done(speech, answered = true)
            else -> CommandOutcome.NotUnderstood(response.obj("data")?.string("code") ?: speech ?: "not understood")
        }
    }

    private fun finish(outcome: CommandOutcome) = listOf(Step.Finish(outcome))

    private fun failed(reason: String) = CommandOutcome.Failed(reason)

    override fun notHomeAssistant() = failed("not Home Assistant")
    override fun badToken() = failed("bad token")
    override fun unexpected(type: String) = failed("unexpected $type")
    override fun unreachable() = failed("unreachable")
    override fun closed() = failed("closed")
    override fun timedOut(stage: String) = failed("timeout after $stage")

    private companion object {
        /** Error codes that mean the words were not understood, rather than that something broke. */
        val NOT_UNDERSTOOD = setOf("stt-no-text-recognized", "intent-not-supported", "no_intent_match")
    }
}
