// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

import android.util.Log
import it.manentlabs.app.audio.M4aReader
import it.manentlabs.app.audio.VoiceDetector
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The speech check every closed part goes through before it is filed. Runs
 * on the journal's thread only, so it keeps one detector for all parts.
 */
@Singleton
class SpeechChecker @Inject constructor(private val detectors: VoiceDetectors) {
    private var detector: VoiceDetector? = null

    fun check(file: File): SpeechVerdict {
        val pcm = try {
            M4aReader.read(file)
        } catch (e: IOException) {
            Log.w(TAG, "Speech check could not decode ${file.name}: ${e.message}")
            return SpeechVerdict.Unknown
        }
        val vad = detector ?: detectors.create()?.also { detector = it } ?: return SpeechVerdict.Unknown
        val verdict = SpeechScan.scan(pcm, vad)
        if (verdict == SpeechVerdict.Unknown) {
            vad.close()
            detector = null
        }
        return verdict
    }

    private companion object {
        const val TAG = "Manent:SpeechCheck"
    }
}
