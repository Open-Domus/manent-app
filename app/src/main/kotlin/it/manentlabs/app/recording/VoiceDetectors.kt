// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import it.manentlabs.app.audio.SileroVad
import it.manentlabs.app.audio.VoiceDetector
import javax.inject.Inject
import javax.inject.Singleton

/** Makes speech detectors from the bundled model, read from the assets once. */
@Singleton
class VoiceDetectors @Inject constructor(@ApplicationContext private val context: Context) {
    private val model: ByteArray? by lazy {
        try {
            context.assets.open(SileroVad.ASSET).use { it.readBytes() }
        } catch (e: java.io.IOException) {
            Log.e(TAG, "The speech model is missing", e)
            null
        }
    }

    /** A fresh detector, or null when the model or the native runtime cannot be loaded. */
    fun create(): VoiceDetector? {
        val bytes = model ?: return null
        return try {
            SileroVad(bytes)
        } catch (e: Exception) {
            Log.e(TAG, "Speech detector unavailable", e)
            null
        } catch (e: LinkageError) {
            Log.e(TAG, "Speech detector runtime did not load", e)
            null
        }
    }

    private companion object {
        const val TAG = "Manent:Vad"
    }
}
