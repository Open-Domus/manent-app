// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable.omi

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import it.manentlabs.app.wearable.RecorderControl
import it.manentlabs.app.wearable.RecorderState
import it.manentlabs.app.wearable.RecordingInput
import it.manentlabs.app.wearable.Wearable
import it.manentlabs.app.wearable.WearableSignal
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Keeps the pendant linked while nothing records, so that its button can
 * start a recording, and runs the "Capture all day" automation.
 */
@AndroidEntryPoint
class OmiLinkService : LifecycleService() {
    @Inject lateinit var link: OmiLink
    @Inject lateinit var wearable: Wearable
    @Inject lateinit var recorder: RecorderControl

    private var started = false
    private var observers: Job? = null
    private val startPending = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        try {
            ServiceCompat.startForeground(
                this,
                OmiNotification.ID,
                OmiNotification.build(this, link.state.value),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
            )
        } catch (e: RuntimeException) {
            // Last resort: start() checks the permission first, so this should not happen.
            Log.w(TAG, "Could not go foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            link.disconnect()
            finish()
            return START_NOT_STICKY
        }
        if (!hasBluetoothConnect(this)) {
            finish()
            return START_NOT_STICKY
        }
        // Every start is a nudge: connect() does nothing while a link is open or awaited.
        link.connect()
        if (!started) {
            started = true
            observe()
        }
        return START_STICKY
    }

    private fun finish() {
        observers?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun observe() {
        observers = lifecycleScope.launch {
            launch { notifyStates() }
            launch { link.buttons.filter { it == OmiButton.TAP }.collect { launch { onTap() } } }
            launch { runAllDay() }
        }
    }

    private suspend fun notifyStates() {
        val notifications = getSystemService(NotificationManager::class.java)
        link.state.collect { notifications.notify(OmiNotification.ID, OmiNotification.build(this, it)) }
    }

    private suspend fun runAllDay() {
        combine(recorder.captureAllDay, link.ready, recorder.state, PendantPolicy::allDay)
            .distinctUntilChanged()
            .collect { action ->
                when (action) {
                    AllDayAction.START_AMBIENT -> if (!recorder.startAmbient()) Log.w(TAG, "All-day capture refused")
                    AllDayAction.STOP -> recorder.stop()
                    AllDayAction.NONE -> Unit
                }
            }
    }

    private suspend fun onTap() {
        when (PendantPolicy.onTap(startPending.get(), recorder.captureAllDay.value, recorder.state.value)) {
            TapAction.IGNORE -> Unit
            TapAction.MARK -> wearable.signal(if (recorder.markMoment()) WearableSignal.MARKED else WearableSignal.FAILED)
            TapAction.STOP -> {
                recorder.stop()
                wearable.signal(WearableSignal.STOPPED)
            }
            TapAction.START -> {
                if (!startPending.compareAndSet(false, true)) return
                try {
                    wearable.signal(if (startFromWearable()) WearableSignal.STARTED else WearableSignal.FAILED)
                } finally {
                    startPending.set(false)
                }
            }
        }
    }

    /** True once the recorder reports a wearable recording, within 10 s of asking. */
    private suspend fun startFromWearable(): Boolean {
        if (!recorder.startWearableRecording()) return false
        return withTimeoutOrNull(START_CONFIRM_MS) {
            recorder.state.first { it is RecorderState.Active && it.input == RecordingInput.WEARABLE }
        } != null
    }

    companion object {
        private const val ACTION_STOP = "it.manentlabs.app.omi.STOP"
        private const val START_CONFIRM_MS = 10_000L

        /**
         * Starts or nudges the service. A start refused from the background is
         * logged and ignored. Without the Nearby devices permission the
         * connected-device foreground type is refused, so the service is not
         * started at all: a service started as foreground that fails to get
         * there crashes the app.
         */
        fun start(context: Context) {
            if (!hasBluetoothConnect(context)) {
                Log.w(TAG, "Link service not started: no Nearby devices permission")
                return
            }
            try {
                ContextCompat.startForegroundService(context, Intent(context, OmiLinkService::class.java))
            } catch (e: RuntimeException) {
                Log.w(TAG, "Link service start refused", e)
            }
        }

        /** Disconnects the pendant and stops the service. */
        fun stop(context: Context) {
            try {
                context.startService(Intent(context, OmiLinkService::class.java).setAction(ACTION_STOP))
            } catch (e: RuntimeException) {
                Log.w(TAG, "Link service stop refused", e)
            }
        }
    }
}
