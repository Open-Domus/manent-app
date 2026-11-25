// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

import kotlinx.serialization.Serializable
import java.io.File
import java.time.Instant

/** What `POST /recordings` needs to open a session; the device id is added from the pairing. */
data class RecordingStart(
    /** Client-made UUID: the gateway's idempotency key for the session. */
    val id: String,
    val startedAt: Instant,
    val title: String? = null,
    /** True for an episode the wearable opened on its own (all-day capture). */
    val ambient: Boolean = false,
    val timeZoneId: String? = null,
    val utcOffsetSeconds: Int? = null,
    val language: String? = null,
)

/**
 * One part of a recording for `PUT /recordings/{id}/chunks/{seq}`. [audio] is
 * left out for a silent part; if it is given but the file is gone, the part is
 * sent as an interrupted gap so its number still reaches the gateway.
 */
data class ChunkUpload(
    val startedAt: Instant,
    val durationMs: Long,
    val silent: Boolean = false,
    val interrupted: Boolean = false,
    val audio: File? = null,
    /** MIME type of [audio]; `application/octet-stream` is sent when it is unknown. */
    val mimeType: String? = null,
)

/** A marked-moment clip for `POST /notes/voice`. */
data class VoiceNoteUpload(
    val capturedAt: Instant,
    /** The recording the clip was cut from. */
    val recordingId: String,
    /** AAC in MPEG-4. */
    val audio: File,
)

/** Who a speaker in a recording is, as sent to `PUT /recordings/{id}/speakers/{n}`. */
sealed interface SpeakerAssignment {
    data object Me : SpeakerAssignment
    data class Person(val personId: String) : SpeakerAssignment
    /** A new name, 1 to 120 characters; the gateway trims it. */
    data class Name(val name: String) : SpeakerAssignment
    data object Clear : SpeakerAssignment
}

/** A row of the gateway's recent recordings (`GET /recordings`). */
@Serializable
data class GatewayRecording(
    val id: String,
    val title: String = "",
    val startedAt: String,
    /** `recording`, `transcribing`, `done`, or a newer value kept as sent. */
    val state: String,
    val speakers: Int = 0,
    val namedSpeakers: Int = 0,
)

/** One voice the gateway told apart in a recording, numbered from 1 in order of first appearance. */
@Serializable
data class RecordingSpeaker(
    val speaker: Int,
    val isSelf: Boolean = false,
    val personId: String? = null,
    val name: String? = null,
    val talkMs: Long = 0,
    /** The first few lines they said. */
    val samples: List<String> = emptyList(),
) {
    val isNamed: Boolean get() = isSelf || personId != null || name != null
}

/** A person the gateway knows, from `GET /people`. */
@Serializable
data class GatewayPerson(
    val id: String,
    val canonicalName: String = "",
    val isSelf: Boolean = false,
)

@Serializable
internal data class RecordingStartBody(
    val id: String,
    val title: String? = null,
    val origin: String? = null,
    val startedAt: String,
    val startedTimeZoneId: String? = null,
    val startedUtcOffsetSeconds: Int? = null,
    val language: String? = null,
    val deviceId: String? = null,
)

@Serializable
internal data class ChunkBody(
    val startedAt: String,
    val durationMs: Long,
    val silent: Boolean? = null,
    val interrupted: Boolean? = null,
)

@Serializable
internal data class FinishBody(val chunkCount: Int, val endedAt: String)

@Serializable
internal data class VoiceNoteBody(
    val id: String,
    val text: String,
    val capturedAt: String,
    val surface: String,
    val deviceId: String? = null,
    val language: String? = null,
    val capturedTimeZoneId: String? = null,
    val capturedUtcOffsetSeconds: Int? = null,
    val recordingId: String,
)

@Serializable
internal data class RecordingList(val recordings: List<GatewayRecording> = emptyList())

@Serializable
internal data class SpeakerList(val speakers: List<RecordingSpeaker> = emptyList())

@Serializable
internal data class PeoplePage(val items: List<GatewayPerson> = emptyList())

@Serializable
internal data class TranscriptionReply(val available: Boolean = false, val text: String? = null)
