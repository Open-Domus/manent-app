// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.transcribe

import android.content.Context
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import it.manentlabs.app.audio.PcmFormat
import it.manentlabs.app.gateway.Gateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** How long the last transcription took, for the settings: [elapsedMs] for [audioMs] of audio. */
data class TranscriptionTiming(val audioMs: Long, val elapsedMs: Long, val heard: Boolean)

/**
 * Turns a command's audio into text through the paired gateway, for webhook
 * and phone destinations (Home Assistant transcribes for itself). The audio
 * is written to a temporary WAV that is deleted as soon as the gateway answers.
 */
@Singleton
class CommandTranscriber @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gateway: Gateway,
) {
    private val _lastTiming = MutableStateFlow<TranscriptionTiming?>(null)

    /** The last transcription's timing; kept in memory only. */
    val lastTiming: StateFlow<TranscriptionTiming?> = _lastTiming.asStateFlow()

    /** The command's text, trimmed; null when the gateway is unavailable or heard nothing. */
    suspend fun transcribe(pcm: ShortArray): String? = withContext(Dispatchers.IO) {
        if (gateway.transcriberBlock.value != null) return@withContext null
        val folder = File(context.cacheDir, FOLDER).apply { mkdirs() }
        val wav = File(folder, "command-${UUID.randomUUID()}.wav")
        val started = SystemClock.elapsedRealtime()
        try {
            wav.writeBytes(Wav.of(pcm))
            val text = gateway.transcribe(wav, Locale.getDefault().toLanguageTag())?.trim()?.takeIf { it.isNotEmpty() }
            _lastTiming.value = TranscriptionTiming(
                audioMs = PcmFormat.samplesToMs(pcm.size.toLong()),
                elapsedMs = SystemClock.elapsedRealtime() - started,
                heard = text != null,
            )
            text
        } catch (e: IOException) {
            Log.w(TAG, "Could not write the command audio: ${e::class.java.simpleName}")
            null
        } finally {
            wav.delete()
        }
    }

    private companion object {
        const val TAG = "Manent:Assist"
        const val FOLDER = "voice-commands"
    }
}
