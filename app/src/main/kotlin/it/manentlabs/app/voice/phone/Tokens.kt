// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.phone

/** A word of a command, lower-cased, with where it sits in the original text. */
internal data class Token(val text: String, val start: Int, val end: Int)

/**
 * Splits a command into words for the rules. Clock times (`7:30`, `7.30`)
 * stay one token; digits and letters split apart (`7am` is `7`, `am`);
 * apostrophes separate (`mezz'ora` is `mezz`, `ora`).
 */
internal object Tokens {
    private val WORD = Regex("""\d{1,2}[:.]\d{2}(?!\d)|\d+|\p{L}+""")

    /** Lower-cases [text] character by character, so positions still match the original. */
    fun lower(text: String): String = String(CharArray(text.length) { text[it].lowercaseChar() })

    fun of(lower: String): List<Token> = WORD.findAll(lower).map { Token(it.value, it.range.first, it.range.last + 1) }.toList()
}

/** Whether the tokens from [from] are exactly [words]. */
internal fun List<Token>.startsWith(from: Int, vararg words: String): Boolean {
    if (from < 0 || from + words.size > size) return false
    return words.indices.all { this[from + it].text == words[it] }
}
