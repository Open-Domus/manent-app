// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * The three things the app reads from the gateway's large `GET /status`
 * answer. Everything else in it is ignored.
 *
 * @property dictationEnabled null when the gateway did not say.
 */
data class GatewayStatus(
    val experimental: Boolean,
    val recordingsOffered: Boolean,
    val dictationEnabled: Boolean?,
) {
    /** The upload gate: the gateway takes recordings now. */
    val acceptsRecordings: Boolean get() = experimental && recordingsOffered

    val recordingAvailability: RecordingAvailability
        get() = when {
            !acceptsRecordings -> RecordingAvailability.UNAVAILABLE
            dictationEnabled == false -> RecordingAvailability.TRANSCRIPTION_OFF
            else -> RecordingAvailability.AVAILABLE
        }

    companion object {
        private const val RECORDINGS_VERSION = 1.0

        /**
         * Reads the answer leniently: a field of the wrong type counts as
         * absent, so only a body that is not a JSON object fails.
         */
        fun parse(body: String): GatewayStatus {
            val root = GatewayJson.parseToJsonElement(body) as? JsonObject
                ?: throw IllegalArgumentException("The status is not a JSON object")
            val dictation = root["dictation"] as? JsonObject
            return GatewayStatus(
                experimental = root["experimental"].bool() ?: false,
                recordingsOffered = offersRecordings(root["capabilities"]),
                dictationEnabled = dictation?.get("enabled").bool(),
            )
        }

        /** The `recordings` capability counts when both bounds are numbers and version 1 lies between them. */
        private fun offersRecordings(capabilities: JsonElement?): Boolean {
            val range = (capabilities as? JsonObject)?.get("recordings") as? JsonObject ?: return false
            val min = range["min"].number() ?: return false
            val max = range["max"].number() ?: return false
            return min <= RECORDINGS_VERSION && RECORDINGS_VERSION <= max
        }

        private fun JsonElement?.bool(): Boolean? = (this as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull

        private fun JsonElement?.number(): Double? = (this as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull
    }
}

/** Whether the recording screen may start a recording, as the gateway last said. */
enum class RecordingAvailability { AVAILABLE, TRANSCRIPTION_OFF, UNAVAILABLE }
