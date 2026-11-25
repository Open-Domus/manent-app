// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

import android.util.Log
import it.manentlabs.app.gateway.Gateway
import it.manentlabs.app.gateway.GatewayError
import it.manentlabs.app.gateway.VoiceNoteUpload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saves a marked moment to the gateway as a voice note. Not queued: without a
 * gateway, or if the call fails, the moment is dropped. The clip is deleted
 * afterwards either way. Runs on its own scope, so it outlives the service.
 */
@Singleton
class MomentSender @Inject constructor(private val gateway: Gateway) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun send(clip: File, markedAt: Instant, recordingId: String) {
        scope.launch {
            try {
                val api = gateway.api.value
                if (api == null) {
                    Log.i(TAG, "Marked moment dropped: no gateway")
                } else {
                    api.sendVoiceNote(VoiceNoteUpload(capturedAt = markedAt, recordingId = recordingId, audio = clip))
                }
            } catch (e: GatewayError) {
                Log.w(TAG, "Marked moment not saved: ${e::class.simpleName} ${e.message}")
            } finally {
                clip.delete()
            }
        }
    }

    private companion object {
        const val TAG = "Manent:Moment"
    }
}
