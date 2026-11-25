// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.phone

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** An app on the phone, as the settings list it. */
data class PhoneApp(val packageName: String, val label: String)

/**
 * Which installed apps can carry out each kind of phone action, found by
 * asking Android who answers a sample intent of that kind. The manifest
 * declares these intents under `<queries>`, so Android lets the app see them.
 */
@Singleton
class PhoneApps @Inject constructor(@ApplicationContext private val context: Context) {
    private val packages = context.packageManager

    /** The apps answering [kind]'s sample intent, one per package, by label; this app left out. */
    fun candidates(kind: PhoneActionKind): List<PhoneApp> =
        query(probe(kind))
            .map { it.activityInfo.packageName to it.loadLabel(packages).toString() }
            .filter { (pkg, _) -> pkg != context.packageName }
            .distinctBy { it.first }
            .map { (pkg, label) -> PhoneApp(pkg, label) }
            .sortedBy { it.label.lowercase() }

    /** The app that carries out [kind]: the owner's [chosen] one, the only one, or the system default. */
    fun pick(kind: PhoneActionKind, chosen: String?): AppPick =
        AppPick.of(chosen, candidates(kind).map { it.packageName }, systemDefault(probe(kind)))

    /** The app Android opens for [intent] without a chooser, if any. */
    private fun systemDefault(intent: Intent): String? {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            packages.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packages.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        // With no default, Android resolves to its own chooser, which lives in the "android" package.
        return info?.activityInfo?.packageName?.takeIf { it != "android" }
    }

    private fun query(intent: Intent): List<ResolveInfo> = if (Build.VERSION.SDK_INT >= 33) {
        packages.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packages.queryIntentActivities(intent, 0)
    }

    companion object {
        /** A sample intent each app able to do [kind] answers. */
        fun probe(kind: PhoneActionKind): Intent = when (kind) {
            PhoneActionKind.TIMER -> Intent(AlarmClock.ACTION_SET_TIMER)
            PhoneActionKind.ALARM -> Intent(AlarmClock.ACTION_SET_ALARM)
            PhoneActionKind.NAVIGATE -> Intent(Intent.ACTION_VIEW, "geo:0,0?q=station".toUri())
            PhoneActionKind.MUSIC -> Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            PhoneActionKind.OPEN_APP -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            PhoneActionKind.CALL -> Intent(Intent.ACTION_DIAL, "tel:".toUri())
            PhoneActionKind.CALENDAR -> Intent(Intent.ACTION_INSERT).setType("vnd.android.cursor.dir/event")
            PhoneActionKind.SHARE -> Intent(Intent.ACTION_SEND).setType("text/plain")
        }
    }
}
