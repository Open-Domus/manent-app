// SPDX-License-Identifier: MIT

package it.manentlabs.app.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads replies aloud with the phone's text-to-speech engine, through
 * headphones when connected, otherwise the speaker. It plays as media, at the
 * media volume the side keys set: the assistant volume is its own and only
 * adjustable while Google's assistant talks, so it is easily left at zero and
 * the reply silenced. Other audio ducks while it speaks. The engine starts on
 * the first reply and stays up; a reply that comes while it starts waits for it.
 */
@Singleton
class AnswerSpeaker @Inject constructor(@ApplicationContext private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()

    private var engine: TextToSpeech? = null
    private var ready = false
    private val waiting = ArrayDeque<String>()

    @Synchronized
    fun speak(text: String) {
        if (ready) {
            say(text)
            return
        }
        waiting.addLast(text)
        if (engine == null) engine = TextToSpeech(context, ::onInit)
    }

    @Synchronized
    private fun onInit(status: Int) {
        val tts = engine ?: return
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "Text-to-speech did not start ($status)")
            tts.shutdown()
            engine = null
            waiting.clear()
            return
        }
        tts.setAudioAttributes(attributes)
        val language = tts.setLanguage(Locale.getDefault())
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "No voice for ${Locale.getDefault()}; using the engine's default")
        }
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = release()

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = release()
            },
        )
        ready = true
        while (waiting.isNotEmpty()) say(waiting.removeFirst())
    }

    private fun say(text: String) {
        val tts = engine ?: return
        audio.requestAudioFocus(focus)
        if (tts.speak(text, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString()) != TextToSpeech.SUCCESS) {
            Log.w(TAG, "Text-to-speech refused a reply")
            release()
        }
    }

    @Synchronized
    private fun release() {
        if (engine?.isSpeaking != true) audio.abandonAudioFocusRequest(focus)
    }

    private companion object {
        const val TAG = "Manent:Speech"
    }
}
