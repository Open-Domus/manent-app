// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

import java.util.Locale

/** Elapsed recording time as the recorder card shows it: m:ss, or h:mm:ss from an hour on. */
object ElapsedFormat {
    fun format(ms: Long): String {
        val seconds = maxOf(0, ms) / 1000
        val h = seconds / 3600
        val m = seconds / 60 % 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
    }
}

/** Where a recording waiting on this phone stands. Fully sent recordings leave the phone. */
sealed interface LocalStatus {
    data object Waiting : LocalStatus
    data class Unsent(val parts: Int) : LocalStatus
    data class Refused(val reason: String) : LocalStatus

    companion object {
        fun of(recording: LocalRecording): LocalStatus {
            val session = recording.session
            return when {
                session.refusal != null -> Refused(session.refusal)
                recording.unsentParts == 0 -> Waiting
                else -> Unsent(recording.unsentParts)
            }
        }
    }
}

/** A recording's start as the lists show it: "Tue 24 Nov, 19:16" in the user's language and zone. */
object RecordingDates {
    fun format(instant: java.time.Instant, zone: java.time.ZoneId, locale: Locale): String =
        java.time.format.DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", locale).format(instant.atZone(zone))
}
