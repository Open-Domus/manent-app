// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import it.manentlabs.app.wearable.RecordingInput
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the app in the foreground while a recording is starting, running or
 * closing, and carries its notification. The recording itself lives in
 * [Recorder]; this service never resumes one after the process died.
 */
@AndroidEntryPoint
class RecordingService : LifecycleService() {
    @Inject lateinit var recorder: Recorder

    private var observing = false
    private var lastStartId = 0

    /** The token of the last start this instance took to the foreground, handed back on destroy. */
    private var enteredToken = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        lastStartId = startId
        when (intent?.action) {
            ACTION_ENTER -> enter(intent)
            ACTION_PAUSE -> recorder.pause()
            ACTION_RESUME -> recorder.resume()
            ACTION_STOP -> recorder.stopFromUser()
            else -> {
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
        if (!observing) {
            observing = true
            observe()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        recorder.onServiceDestroyed(enteredToken)
        super.onDestroy()
    }

    private fun enter(intent: Intent) {
        val input = RecordingInput.entries.getOrNull(intent.getIntExtra(EXTRA_INPUT, -1)) ?: RecordingInput.PHONE
        val token = intent.getLongExtra(EXTRA_TOKEN, 0)
        val entered = try {
            ServiceCompat.startForeground(
                this,
                RecordingNotification.ID,
                RecordingNotification.build(this, recorder.status.value, recorder.wearableName),
                foregroundType(input),
            )
            true
        } catch (e: RuntimeException) {
            // Last resort: Recorder checks canEnter first, so this should not happen.
            Log.w(TAG, "Could not go foreground", e)
            false
        }
        if (entered) enteredToken = token
        recorder.onForeground(token, entered)
    }

    private fun foregroundType(input: RecordingInput): Int = when {
        input == RecordingInput.WEARABLE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        input == RecordingInput.PHONE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        else -> 0
    }

    private fun observe() {
        val notifications = getSystemService(NotificationManager::class.java)
        lifecycleScope.launch {
            recorder.status.collect { status ->
                if (status is RecordingStatus.Active) {
                    notifications.notify(RecordingNotification.ID, RecordingNotification.build(this@RecordingService, status, recorder.wearableName))
                }
            }
        }
        lifecycleScope.launch {
            recorder.busy.collect { busy ->
                // A stale "idle" seen after a new start already came in must not stop the service under it.
                if (!busy && !recorder.busy.value) {
                    ServiceCompat.stopForeground(this@RecordingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf(lastStartId)
                }
            }
        }
    }

    companion object {
        private const val TAG = "Manent:RecService"
        private const val ACTION_ENTER = "it.manentlabs.app.recording.ENTER"
        internal const val ACTION_PAUSE = "it.manentlabs.app.recording.PAUSE"
        internal const val ACTION_RESUME = "it.manentlabs.app.recording.RESUME"
        internal const val ACTION_STOP = "it.manentlabs.app.recording.STOP"
        private const val EXTRA_INPUT = "input"
        private const val EXTRA_TOKEN = "token"

        /**
         * Whether the service may take the foreground type [input] needs. It is
         * checked before starting the service, because a service started as
         * foreground that fails to get there crashes the app.
         */
        fun canEnter(context: Context, input: RecordingInput): Boolean = when (input) {
            RecordingInput.PHONE -> granted(context, Manifest.permission.RECORD_AUDIO)
            RecordingInput.WEARABLE ->
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S || granted(context, Manifest.permission.BLUETOOTH_CONNECT)
        }

        private fun granted(context: Context, permission: String): Boolean =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        /** Brings the service to the foreground for a recording from [input]; the answer goes to [Recorder.onForeground]. */
        fun enterIntent(context: Context, input: RecordingInput, token: Long): Intent =
            Intent(context, RecordingService::class.java)
                .setAction(ACTION_ENTER)
                .putExtra(EXTRA_INPUT, input.ordinal)
                .putExtra(EXTRA_TOKEN, token)

        internal fun actionIntent(context: Context, action: String): Intent =
            Intent(context, RecordingService::class.java).setAction(action)
    }
}
