// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.phone

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import it.manentlabs.app.R
import it.manentlabs.app.voice.CommandOutcome
import it.manentlabs.app.voice.routes.VoiceSettings
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries out a parsed [PhoneAction] by starting the app that does it, as a
 * new task from the background. Refusals (no app, an app to choose, an
 * unknown contact) are reasons for the log only; the wearer feels a failure.
 */
@Singleton
class PhoneActions @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apps: PhoneApps,
    private val contacts: ContactBook,
    private val permissions: PhonePermissions,
    private val settings: VoiceSettings,
) {
    fun carryOut(action: PhoneAction): CommandOutcome {
        val intent = when (val built = intentFor(action)) {
            is Built.Ready -> built.intent
            is Built.Refused -> return CommandOutcome.Failed(built.reason)
        }
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            CommandOutcome.Done()
        } catch (_: ActivityNotFoundException) {
            CommandOutcome.Failed("no app takes it")
        } catch (e: SecurityException) {
            Log.w(TAG, "Android refused to start the ${action.kind} app")
            CommandOutcome.Failed("refused by Android")
        }
    }

    private sealed interface Built {
        data class Ready(val intent: Intent) : Built
        data class Refused(val reason: String) : Built
    }

    private fun intentFor(action: PhoneAction): Built = when (action) {
        is PhoneAction.Timer -> clock(
            Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, action.seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
        )
        is PhoneAction.Alarm -> clock(
            Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, action.time.hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, action.time.minute)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
        )
        is PhoneAction.Navigate -> pinned(action.kind) { pkg ->
            val query = Uri.encode(action.destination)
            val uri = if (pkg == GOOGLE_MAPS) "google.navigation:q=$query" else "geo:0,0?q=$query"
            Intent(Intent.ACTION_VIEW, uri.toUri())
        }
        is PhoneAction.Music -> pinned(action.kind) {
            Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                .putExtra(SearchManager.QUERY, action.query)
                .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
        }
        is PhoneAction.Calendar -> pinned(action.kind) { calendar(action) }
        is PhoneAction.Share -> pinned(action.kind) {
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, action.text)
        }
        is PhoneAction.OpenApp -> openApp(action.name)
        is PhoneAction.Call -> call(action.who)
    }

    private fun clock(intent: Intent): Built =
        if (intent.resolveActivity(context.packageManager) == null) Built.Refused("no clock app on this phone") else Built.Ready(intent)

    private fun pinned(kind: PhoneActionKind, build: (String) -> Intent): Built =
        when (val pick = apps.pick(kind, settings.state.value.phoneApps[kind])) {
            is AppPick.Use -> Built.Ready(build(pick.packageName).setPackage(pick.packageName))
            AppPick.NoApp -> Built.Refused("no app on this phone handles $kind")
            AppPick.MustChoose -> Built.Refused("choose an app for $kind in the voice-command settings")
        }

    private fun calendar(action: PhoneAction.Calendar): Intent {
        val zone = ZoneId.systemDefault()
        val begin = action.date.atTime(action.time?.hour ?: 0, action.time?.minute ?: 0).atZone(zone)
        val end = if (action.time == null) begin.plusHours(24) else begin.plusHours(1)
        val title = action.title ?: context.getString(R.string.voice_event_default_title)
        return Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin.toInstant().toEpochMilli())
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.toInstant().toEpochMilli())
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, action.time == null)
    }

    private fun openApp(name: String): Built {
        val launchable = apps.candidates(PhoneActionKind.OPEN_APP)
        val index = NameMatching.app(name, launchable.map { it.label }) ?: return Built.Refused("no app called “$name”")
        val intent = context.packageManager.getLaunchIntentForPackage(launchable[index].packageName)
            ?: return Built.Refused("no app called “$name”")
        return Built.Ready(intent)
    }

    private fun call(who: String): Built {
        val number = NameMatching.phoneNumber(who) ?: when (val found = contacts.find(who)) {
            is ContactBook.Lookup.Found -> found.number
            ContactBook.Lookup.NoOne -> return Built.Refused("no contact by that name")
            ContactBook.Lookup.Several -> return Built.Refused("more than one contact by that name")
            ContactBook.Lookup.NotAllowed -> return Built.Refused("contacts not allowed; allow them in the voice-command settings")
        }
        val action = if (permissions.canCall()) Intent.ACTION_CALL else Intent.ACTION_DIAL
        return Built.Ready(Intent(action, Uri.fromParts("tel", number, null)))
    }

    private companion object {
        const val TAG = "Manent:Assist"
        const val GOOGLE_MAPS = "com.google.android.apps.maps"
    }
}
