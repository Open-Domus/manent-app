// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import it.manentlabs.app.R
import it.manentlabs.app.wearable.RecordingInput

/**
 * The ongoing notification of a recording: what is happening, a running
 * clock, and Pause or Resume plus Stop (Turn off for an all-day episode).
 * The channel id is the one earlier builds used, so the user's settings carry over.
 */
internal object RecordingNotification {
    const val ID = 0x7EC0
    private const val CHANNEL = "omnesis_recording"

    fun build(context: Context, status: RecordingStatus, wearableName: String): Notification {
        ensureChannel(context)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_recording_mic)
            .setContentIntent(launch)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        val active = status as? RecordingStatus.Active
        val (title, text) = texts(context, active, wearableName)
        builder.setContentTitle(title).setContentText(text)
        val runningSince = active?.runningSince
        if (active != null && runningSince != null) {
            val elapsed = active.elapsedMs(SystemClock.elapsedRealtime())
            builder.setUsesChronometer(true).setShowWhen(true).setWhen(System.currentTimeMillis() - elapsed)
        } else {
            builder.setShowWhen(false)
        }
        if (active != null) {
            if (active.paused) {
                builder.addAction(0, context.getString(R.string.recording_resume), action(context, RecordingService.ACTION_RESUME))
            } else {
                builder.addAction(0, context.getString(R.string.recording_pause), action(context, RecordingService.ACTION_PAUSE))
            }
            val stopLabel = if (active.ambient) R.string.recording_turn_off else R.string.recording_stop
            builder.addAction(0, context.getString(stopLabel), action(context, RecordingService.ACTION_STOP))
        }
        return builder.build()
    }

    private fun texts(context: Context, status: RecordingStatus.Active?, wearableName: String): Pair<String, String> {
        val device = wearableName.ifBlank { context.getString(R.string.recording_wearable_fallback) }
        val wearable = status?.input == RecordingInput.WEARABLE
        return when {
            status != null && status.paused && status.inputLost && wearable ->
                context.getString(R.string.recording_notification_paused) to
                    context.getString(R.string.recording_notification_wearable_lost, device)
            status != null && status.paused && status.inputLost ->
                context.getString(R.string.recording_notification_paused) to context.getString(R.string.recording_notification_mic_busy)
            status != null && status.paused ->
                context.getString(R.string.recording_notification_paused) to context.getString(R.string.recording_notification_paused_text)
            status != null && status.ambient ->
                context.getString(R.string.recording_notification_ambient) to
                    context.getString(R.string.recording_notification_ambient_text, device)
            else -> context.getString(R.string.recording_notification_active) to
                context.getString(
                    R.string.recording_notification_active_text,
                    if (wearable) device else context.getString(R.string.recording_microphone),
                )
        }
    }

    private fun action(context: Context, action: String): PendingIntent =
        PendingIntent.getService(
            context,
            action.hashCode(),
            RecordingService.actionIntent(context, action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Created every time, so its name follows the current language. */
    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.recording_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.recording_channel_description)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
