// SPDX-License-Identifier: MIT

package it.manentlabs.app.omnesis

import android.content.Context
import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.manentlabs.app.feature.assist.CommandTranscriber
import it.manentlabs.app.feature.assist.CommandTranscribers
import it.manentlabs.app.feature.assist.TranscriberKind
import it.manentlabs.app.feature.recording.ChunkPolicy
import it.manentlabs.app.feature.recording.MarkedMomentSink
import it.manentlabs.app.feature.recording.RecordingSessionProvider
import it.manentlabs.app.feature.recording.ui.RecordingAvailability
import it.manentlabs.app.transport.GatewayException
import it.manentlabs.app.transport.dto.VoiceNoteBody
import it.manentlabs.app.R
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OmnesisModule {
    /** The recorder's way to the gateway; null while unpaired, and the upload waits. */
    @Provides
    fun provideRecordingSessionProvider(sessions: GatewaySessions): RecordingSessionProvider =
        RecordingSessionProvider {
            sessions.session?.let {
                ClientRecordingsGateway(it.recordings, it.gateway, it.search, it.pairing.deviceId)
            }
        }

    @Provides
    fun provideRecordingAvailability(sessions: GatewaySessions): RecordingAvailability = sessions

    /**
     * A marked moment's clip becomes a voice note naming the recording it was
     * cut from. Unlike the Omnesis app there is no offline notes queue: a clip
     * the gateway does not take now is dropped.
     */
    @Provides
    fun provideMarkedMomentSink(sessions: GatewaySessions): MarkedMomentSink =
        MarkedMomentSink { clip, recordingId ->
            try {
                val session = sessions.session ?: return@MarkedMomentSink
                val at = Instant.ofEpochMilli(clip.markedAtMs)
                val zone = ZoneId.systemDefault()
                session.notes.createVoice(
                    VoiceNoteBody(
                        id = UUID.randomUUID().toString(),
                        text = "",
                        capturedAt = at.toString(),
                        surface = MARKED_MOMENT_SURFACE,
                        deviceId = session.pairing.deviceId,
                        language = Locale.getDefault().toLanguageTag(),
                        capturedTimeZoneId = zone.id,
                        capturedUtcOffsetSeconds = zone.rules.getOffset(at).totalSeconds,
                        recordingId = recordingId,
                    ),
                    clip.file,
                    ChunkPolicy.MIME_TYPE,
                )
            } catch (e: GatewayException) {
                Log.w(TAG, "Marked moment not saved: ${e::class.simpleName}")
            } catch (e: IOException) {
                Log.w(TAG, "Marked moment not saved: ${e.message}")
            } finally {
                clip.file.delete()
            }
        }

    /** Voice commands for webhooks and the phone are transcribed by the gateway. */
    @Provides
    @Singleton
    fun provideCommandTranscribers(@ApplicationContext context: Context, sessions: GatewaySessions): CommandTranscribers =
        CommandTranscribers(
            mapOf(
                TranscriberKind.GATEWAY to GatewayCommandTranscriber(context, sessions),
                TranscriberKind.PHONE to UnsupportedTranscriber(context),
            ),
        )

    const val MARKED_MOMENT_SURFACE = "android-marked-moment"
    private const val TAG = "Manent"
}

class GatewayCommandTranscriber(
    private val context: Context,
    private val sessions: GatewaySessions,
) : CommandTranscriber {
    override fun unavailable(): String? {
        if (sessions.session == null) return context.getString(R.string.app_transcriber_not_paired)
        return if (sessions.status.value?.dictationEnabled == false) {
            context.getString(R.string.app_transcriber_dictation_off)
        } else {
            null
        }
    }

    override suspend fun transcribe(wav: File, locale: String?): String? {
        val session = sessions.session ?: return null
        return try {
            session.transcriber.transcribe(wav, "audio/wav", locale)
        } catch (e: GatewayException) {
            Log.w(TAG, "Gateway transcription failed: ${e::class.simpleName}")
            null
        } catch (e: IOException) {
            Log.w(TAG, "Gateway transcription failed: ${e.message}")
            null
        }
    }

    private companion object {
        const val TAG = "Manent:Assist"
    }
}

/** On-device recognition is not ported yet. */
class UnsupportedTranscriber(private val context: Context) : CommandTranscriber {
    override fun unavailable(): String = context.getString(R.string.app_transcriber_phone_unsupported)
    override suspend fun transcribe(wav: File, locale: String?): String? = null
}
