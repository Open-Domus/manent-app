// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.phone

import java.time.LocalDate

/** What a spoken command asks the phone to do, once parsed. */
sealed interface PhoneAction {
    val kind: PhoneActionKind

    data class Timer(val seconds: Int) : PhoneAction {
        override val kind get() = PhoneActionKind.TIMER
    }

    data class Alarm(val time: ClockTime) : PhoneAction {
        override val kind get() = PhoneActionKind.ALARM
    }

    data class Navigate(val destination: String) : PhoneAction {
        override val kind get() = PhoneActionKind.NAVIGATE
    }

    data class Music(val query: String) : PhoneAction {
        override val kind get() = PhoneActionKind.MUSIC
    }

    data class OpenApp(val name: String) : PhoneAction {
        override val kind get() = PhoneActionKind.OPEN_APP
    }

    /** [who] is what was said after the verb: a number or a contact's name. */
    data class Call(val who: String) : PhoneAction {
        override val kind get() = PhoneActionKind.CALL
    }

    /** A calendar entry; all-day when [time] is null. A null [title] lets the phone's language name it. */
    data class Calendar(val title: String?, val date: LocalDate, val time: ClockTime?) : PhoneAction {
        override val kind get() = PhoneActionKind.CALENDAR
    }

    /** Text for a notes or list app. */
    data class Share(val text: String) : PhoneAction {
        override val kind get() = PhoneActionKind.SHARE
    }
}

/**
 * The kinds of phone action, in the order the settings list them. The names
 * are stored (`phone_app:<KIND>`), so they must not change.
 */
enum class PhoneActionKind(val appChoosable: Boolean) {
    TIMER(false),
    ALARM(false),
    NAVIGATE(true),
    MUSIC(true),
    OPEN_APP(false),
    CALL(false),
    CALENDAR(true),
    SHARE(true),
}

/** A time of day on a 24-hour clock. */
data class ClockTime(val hour: Int, val minute: Int) {
    val isValid: Boolean get() = hour in 0..23 && minute in 0..59
}
