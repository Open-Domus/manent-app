// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.phone

/**
 * Spoken lengths of time, in seconds: the sum of every "<number> <unit>"
 * ("un'ora e 10 minuti"), the fixed phrases for half and quarter hours, and
 * "… e mezzo" / "… and a half" adding half a unit.
 */
internal object Durations {
    private val UNITS: Map<String, Int> = buildMap {
        listOf("ora", "ore", "hour", "hours", "hr", "h").forEach { put(it, 3600) }
        listOf("minuto", "minuti", "minute", "minutes", "min").forEach { put(it, 60) }
        listOf("secondo", "secondi", "second", "seconds", "sec").forEach { put(it, 1) }
    }

    private val PHRASES: List<Pair<List<String>, Int>> = listOf(
        listOf("un", "quarto", "d", "ora") to 900,
        listOf("quarto", "d", "ora") to 900,
        listOf("a", "quarter", "of", "an", "hour") to 900,
        listOf("quarter", "of", "an", "hour") to 900,
        listOf("half", "an", "hour") to 1800,
        listOf("mezz", "ora") to 1800,
        listOf("mezza", "ora") to 1800,
    )

    private val HALVES = listOf(listOf("e", "mezza"), listOf("e", "mezzo"), listOf("and", "a", "half"))

    /** The longest a timer may run. */
    const val MAX_SECONDS = 24 * 3600

    fun isUnit(word: String?): Boolean = word != null && word in UNITS

    /** Whether the token at [at] is a number followed by a unit, the start of a length rather than a clock time. */
    fun startsAt(tokens: List<Token>, at: Int): Boolean {
        val (_, used) = NumberWords.read(tokens, at) ?: return false
        return isUnit(tokens.getOrNull(at + used)?.text)
    }

    /** The total length said in [tokens], or null when none is said. */
    fun seconds(tokens: List<Token>): Int? {
        var total = 0
        var found = false
        var i = 0
        while (i < tokens.size) {
            val phrase = PHRASES.firstOrNull { (words, _) -> tokens.startsWith(i, *words.toTypedArray()) }
            if (phrase != null) {
                total += phrase.second
                found = true
                i += phrase.first.size
                continue
            }
            val number = NumberWords.read(tokens, i)
            val unit = number?.let { UNITS[tokens.getOrNull(i + it.second)?.text] }
            if (number == null || unit == null) {
                i++
                continue
            }
            total += number.first * unit
            found = true
            i += number.second + 1
            val half = HALVES.firstOrNull { tokens.startsWith(i, *it.toTypedArray()) }
            if (half != null) {
                total += unit / 2
                i += half.size
            }
        }
        return total.takeIf { found }
    }

    /** A length a timer can be set to (1 s to 24 h), or null. */
    fun timer(tokens: List<Token>): Int? = seconds(tokens)?.takeIf { it in 1..MAX_SECONDS }
}
