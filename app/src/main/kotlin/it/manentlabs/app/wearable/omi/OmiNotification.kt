// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable.omi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import it.manentlabs.app.R
import it.manentlabs.app.wearable.omi.ui.OmiText

/**
 * The quiet, ongoing notification of the link service. Channel and id are the
 * ones earlier builds used, so the user's channel settings carry over.
 */
internal object OmiNotification {
    const val ID = 0x7EC1
    private const val CHANNEL = "omnesis_omi"

    fun build(context: Context, state: OmiState): Notification {
        ensureChannel(context)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val tap = launch?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_omi)
            .setContentTitle(OmiText.status(state).resolve(context))
            .setContentText(context.getString(R.string.omi_notification_text))
            .setContentIntent(tap)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) != null) return
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.omi_pendant), NotificationManager.IMPORTANCE_MIN).apply {
            description = context.getString(R.string.omi_notification_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
