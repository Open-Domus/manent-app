// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.phone

/**
 * Clock times as spoken: `7:30`, `7.30`, "sette e mezza", "le otto meno un
 * quarto", "7 pm", "seven in the evening", "mezzogiorno". Times are read
 * only where the words leave no doubt; nothing is guessed.
 */
internal object TimesOfDay {
    /** A time found in a command, and the tokens it spans ([start] inclusive, [end] exclusive). */
    data class Found(val time: ClockTime, val start: Int, val end: Int)

    private val ANCHORS = setOf("alle", "all", "at", "per", "for")
    private val EVENING = setOf("sera", "pomeriggio", "evening", "afternoon", "tonight", "stasera")
    private val MORNING = setOf("mattina", "mattino", "morning")

    /** Small words that may sit between a time and its part of the day ("7 di sera", "7 in the evening"). */
    private val LINKS = setOf("di", "del", "della", "in", "the", "of", "la", "this")
    private val CLOCK = Regex("""(\d{1,2})[:.](\d{2})""")
    private const val DAY_PART_REACH = 3

    /** The first time introduced by "alle", "at", "per le", "for" and the like, or said as noon or midnight. */
    fun anchored(tokens: List<Token>): Found? {
        for (i in tokens.indices) {
            val word = tokens[i].text
            if (word in ANCHORS) {
                var at = i + 1
                if (tokens.getOrNull(at)?.text == "le") at++
                if (tokens.getOrNull(at)?.text == "ore") at++
                read(tokens, at)?.let { (time, end) -> return Found(time, i, end) }
            } else if (isNoonOrMidnight(word)) {
                read(tokens, i)?.let { (time, end) -> return Found(time, i, end) }
            }
        }
        return null
    }

    /** An anchored time, else the first number that is neither an article nor the start of a length. */
    fun any(tokens: List<Token>): Found? {
        anchored(tokens)?.let { return it }
        for (i in tokens.indices) {
            val word = tokens[i].text
            if (word in NumberWords.INDEFINITE) continue
            if (NumberWords.value(word) == null && !CLOCK.matches(word)) continue
            if (Durations.startsAt(tokens, i)) continue
            read(tokens, i)?.let { (time, end) -> return Found(time, i, end) }
        }
        return null
    }

    /** Reads a time starting exactly at [at]; the time and the index after it. */
    fun read(tokens: List<Token>, at: Int): Pair<ClockTime, Int>? {
        val word = tokens.getOrNull(at)?.text ?: return null
        when (word) {
            "mezzogiorno", "noon" -> return ClockTime(12, 0) to at + 1
            "mezzanotte", "midnight" -> return ClockTime(0, 0) to at + 1
        }
        var hour: Int
        var minute = 0
        var i: Int
        val clock = CLOCK.matchEntire(word)
        if (clock != null) {
            hour = clock.groupValues[1].toInt()
            minute = clock.groupValues[2].toInt()
            i = at + 1
        } else {
            val (value, used) = NumberWords.read(tokens, at) ?: return null
            // "for 2 hours" is a length, not two o'clock.
            if (Durations.isUnit(tokens.getOrNull(at + used)?.text)) return null
            hour = value
            i = at + used
            readMinutes(tokens, i, hour)?.let { (h, m, next) ->
                hour = h
                minute = m
                i = next
            }
        }
        if (tokens.startsWith(i, "o", "clock")) i += 2
        when {
            tokens.startsWith(i, "am") -> { if (hour == 12) hour = 0; i += 1 }
            tokens.startsWith(i, "a", "m") -> { if (hour == 12) hour = 0; i += 2 }
            tokens.startsWith(i, "pm") -> { if (hour in 1..11) hour += 12; i += 1 }
            tokens.startsWith(i, "p", "m") -> { if (hour in 1..11) hour += 12; i += 2 }
            else -> dayPart(tokens, i)?.let { (evening, end) ->
                if (evening && hour in 1..11) hour += 12
                i = end
            }
        }
        val time = ClockTime(hour, minute)
        return if (time.isValid) time to i else null
    }

    /** The minutes after an hour: "e mezza", "e un quarto", "meno un quarto", "e 20", "meno 10", or English "7 45". */
    private fun readMinutes(tokens: List<Token>, at: Int, hour: Int): Triple<Int, Int, Int>? {
        val earlier = (hour + 23) % 24
        return when {
            tokens.startsWith(at, "e", "mezza") || tokens.startsWith(at, "e", "mezzo") -> Triple(hour, 30, at + 2)
            tokens.startsWith(at, "e", "un", "quarto") -> Triple(hour, 15, at + 3)
            tokens.startsWith(at, "e", "quarto") -> Triple(hour, 15, at + 2)
            tokens.startsWith(at, "meno", "un", "quarto") -> Triple(earlier, 45, at + 3)
            tokens.startsWith(at, "meno", "quarto") -> Triple(earlier, 45, at + 2)
            tokens.startsWith(at, "e") || tokens.startsWith(at, "meno") -> {
                val (n, used) = NumberWords.read(tokens, at + 1) ?: return null
                if (Durations.isUnit(tokens.getOrNull(at + 1 + used)?.text)) return null
                when {
                    tokens[at].text == "e" && n in 0..59 -> Triple(hour, n, at + 1 + used)
                    tokens[at].text == "meno" && n in 1..59 -> Triple(earlier, 60 - n, at + 1 + used)
                    else -> null
                }
            }
            else -> {
                val word = tokens.getOrNull(at)?.text ?: return null
                if (word in NumberWords.INDEFINITE) return null
                val (n, used) = NumberWords.read(tokens, at) ?: return null
                if (n !in 10..59 || Durations.isUnit(tokens.getOrNull(at + used)?.text)) return null
                Triple(hour, n, at + used)
            }
        }
    }

    /**
     * A part of the day within three words after a time: true for evening,
     * false for morning, and where the time phrase ends. The phrase takes in
     * the part of the day only when just small words sit between ("7 di
     * sera"), so "7 domani sera" keeps "domani sera" for the rest of the command.
     */
    private fun dayPart(tokens: List<Token>, from: Int): Pair<Boolean, Int>? {
        var joined = true
        for (k in from until minOf(tokens.size, from + DAY_PART_REACH)) {
            val word = tokens[k].text
            val evening = word in EVENING
            if (evening || word in MORNING) return evening to (if (joined) k + 1 else from)
            if (word !in LINKS) joined = false
        }
        return null
    }

    private fun isNoonOrMidnight(word: String) = word in setOf("mezzogiorno", "noon", "mezzanotte", "midnight")
}
