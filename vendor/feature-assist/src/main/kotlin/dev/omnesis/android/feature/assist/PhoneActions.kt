// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Lorenzo Farnararo

package dev.omnesis.android.feature.assist

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/** The kinds of things a spoken command can ask this phone to do: the standard catalogue. */
enum class PhoneActionKind(
    @StringRes val label: Int,
    /** What to say, as the card shows it. */
    val example: String,
) {
    TIMER(R.string.assist_action_timer, "“timer di 10 minuti”"),
    ALARM(R.string.assist_action_alarm, "“sveglia alle 7:30”"),
    NAVIGATE(R.string.assist_action_navigate, "“naviga verso la stazione”"),
    MUSIC(R.string.assist_action_music, "“metti un po' di jazz”"),
    OPEN_APP(R.string.assist_action_open_app, "“apri la fotocamera”"),
    CALL(R.string.assist_action_call, "“chiama Maya Reeves”"),
    CALENDAR(R.string.assist_action_calendar, "“appuntamento dal dentista domani alle 15”"),
    SHARE(R.string.assist_action_share, "“aggiungi alla lista latte”"),
    ;

    /** Whether the user picks the app among several, rather than taking the phone's default. */
    val choosable: Boolean get() = this == NAVIGATE || this == MUSIC || this == CALENDAR || this == SHARE
}

/** Something a spoken command asks this phone to do. */
sealed interface PhoneAction {
    val kind: PhoneActionKind

    data class Timer(val seconds: Int) : PhoneAction {
        override val kind get() = PhoneActionKind.TIMER
    }

    data class Alarm(val hour: Int, val minute: Int) : PhoneAction {
        override val kind get() = PhoneActionKind.ALARM
    }

    data class Navigate(val destination: String) : PhoneAction {
        override val kind get() = PhoneActionKind.NAVIGATE
    }

    data class PlayMusic(val query: String) : PhoneAction {
        override val kind get() = PhoneActionKind.MUSIC
    }

    data class OpenApp(val name: String) : PhoneAction {
        override val kind get() = PhoneActionKind.OPEN_APP
    }

    /** [who] is a contact's name, or a number as spoken. */
    data class Call(val who: String) : PhoneAction {
        override val kind get() = PhoneActionKind.CALL
    }

    /** An event on [date], at [hour]:[minute], or all day when [hour] is null. */
    data class CalendarEvent(val title: String, val date: LocalDate, val hour: Int?, val minute: Int = 0) : PhoneAction {
        override val kind get() = PhoneActionKind.CALENDAR
    }

    data class ShareText(val text: String) : PhoneAction {
        override val kind get() = PhoneActionKind.SHARE
    }
}

/**
 * Carries out what a command asked for with the apps on this phone: [interpreter]
 * reads the command, [catalog] builds the intent for the app that takes it, and
 * that app is started without anything shown where the action allows it.
 *
 * The command arrives while the app is in the background, where Android starts
 * another app's screen only for an app allowed to display over other apps;
 * without that permission nothing is started and the command is reported failed.
 */
class PhoneActionRunner(
    private val context: Context,
    private val catalog: PhoneActionCatalog,
    private val interpreter: CommandInterpreter = PhoneActionParser,
    private val today: () -> LocalDate = LocalDate::now,
) {
    private val _allowed = MutableStateFlow(Settings.canDrawOverlays(context))

    /** Whether the user allowed the app to display over other apps, as of the last [refresh]. */
    val allowed: StateFlow<Boolean> = _allowed.asStateFlow()

    /** Look again: the permission is changed in the system settings, which tell the app nothing. */
    fun refresh(): Boolean = Settings.canDrawOverlays(context).also { _allowed.value = it }

    fun permitted(): Boolean = refresh()

    fun run(text: String): AssistOutcome {
        val action = interpreter.interpret(text, today()) ?: return AssistOutcome.NotUnderstood("no phone action in it")
        if (!permitted()) return AssistOutcome.Failed("display over other apps not allowed")
        val intent = when (val built = catalog.intentFor(action)) {
            is PhoneActionCatalog.Built.Ready -> built.intent
            is PhoneActionCatalog.Built.Refused -> {
                Log.i(TAG, "Phone action ${action.kind.name.lowercase()} not carried out: ${built.reason}")
                return AssistOutcome.Failed(built.reason)
            }
        }
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Log.i(TAG, "Phone action: ${action.kind.name.lowercase()} with ${intent.`package` ?: "the default app"}")
            AssistOutcome.Done(null)
        } catch (e: ActivityNotFoundException) {
            AssistOutcome.Failed("no app takes it")
        } catch (e: SecurityException) {
            AssistOutcome.Failed("refused: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "Omnesis:Assist"
    }
}
