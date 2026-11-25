// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.phone

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Days as spoken for calendar entries: today, tonight, tomorrow, the day
 * after, or a weekday (the next one; the same weekday means a week ahead).
 */
internal object Days {
    /** A day found in a command: the date, whether it was "tonight", and the tokens it spans. */
    data class Found(val date: LocalDate, val tonight: Boolean, val start: Int, val end: Int)

    private val RELATIVE: List<Triple<List<String>, Long, Boolean>> = listOf(
        Triple(listOf("the", "day", "after", "tomorrow"), 2L, false),
        Triple(listOf("day", "after", "tomorrow"), 2L, false),
        Triple(listOf("dopodomani"), 2L, false),
        Triple(listOf("domani"), 1L, false),
        Triple(listOf("tomorrow"), 1L, false),
        Triple(listOf("oggi"), 0L, false),
        Triple(listOf("today"), 0L, false),
        Triple(listOf("stasera"), 0L, true),
        Triple(listOf("tonight"), 0L, true),
    )

    private val WEEKDAYS: Map<String, DayOfWeek> = buildMap {
        val italian = listOf("lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato", "domenica")
        val english = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
        DayOfWeek.entries.forEachIndexed { i, day ->
            put(italian[i], day)
            put(italian[i].replace('ì', 'i'), day)
            put(english[i], day)
        }
    }

    /** Words that may come just before a weekday and go with it ("on Friday", "il lunedì", "next Monday"). */
    private val BEFORE_WEEKDAY = setOf("on", "il", "next", "this", "questo", "questa")

    /** Words that may come just after a weekday and go with it ("lunedì prossimo"). */
    private val AFTER_WEEKDAY = setOf("prossimo", "prossima")

    fun find(tokens: List<Token>, today: LocalDate): Found? {
        for (i in tokens.indices) {
            for ((words, offset, tonight) in RELATIVE) {
                if (tokens.startsWith(i, *words.toTypedArray())) {
                    return Found(today.plusDays(offset), tonight, i, i + words.size)
                }
            }
            val weekday = WEEKDAYS[tokens[i].text] ?: continue
            val ahead = ((weekday.value - today.dayOfWeek.value + 7) % 7).let { if (it == 0) 7 else it }
            val start = if (tokens.getOrNull(i - 1)?.text in BEFORE_WEEKDAY) i - 1 else i
            val end = if (tokens.getOrNull(i + 1)?.text in AFTER_WEEKDAY) i + 2 else i + 1
            return Found(today.plusDays(ahead.toLong()), false, start, end)
        }
        return null
    }
}
