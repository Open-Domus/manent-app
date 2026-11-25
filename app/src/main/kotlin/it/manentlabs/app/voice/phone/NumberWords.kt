// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.phone

/**
 * Numbers as a transcript spells them: digits, or Italian and English words
 * up to sixty-nine. Italian fuses tens and units (`ventotto`, `trentatré`);
 * both languages may also give them as two words (`venti cinque`, `twenty five`).
 */
internal object NumberWords {
    /** Words that are usually "a" or "one" as an article, not a quantity worth reading as a time. */
    val INDEFINITE = setOf("un", "una", "a", "an")

    private val ITALIAN_UNITS = listOf(
        "zero", "uno", "due", "tre", "quattro", "cinque", "sei", "sette", "otto", "nove",
        "dieci", "undici", "dodici", "tredici", "quattordici", "quindici", "sedici", "diciassette", "diciotto", "diciannove",
    )
    private val ENGLISH_UNITS = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val ITALIAN_TENS = mapOf("venti" to 20, "trenta" to 30, "quaranta" to 40, "cinquanta" to 50, "sessanta" to 60)
    private val ENGLISH_TENS = mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60)

    private val WORDS: Map<String, Int> = buildMap {
        ITALIAN_UNITS.forEachIndexed { value, word -> put(word, value) }
        ENGLISH_UNITS.forEachIndexed { value, word -> put(word, value) }
        put("un", 1)
        put("una", 1)
        put("a", 1)
        put("an", 1)
        put("tré", 3)
        putAll(ITALIAN_TENS)
        putAll(ENGLISH_TENS)
        for ((tens, base) in ITALIAN_TENS) {
            for (unit in 1..9) {
                val word = ITALIAN_UNITS[unit]
                // venti + uno = ventuno, venti + otto = ventotto: the vowel before a vowel drops.
                val stem = if (word.first() in "uo") tens.dropLast(1) else tens
                put(stem + word, base + unit)
                if (unit == 3) put(stem + "tré", base + 3)
            }
        }
    }

    /** The value of a single word or digit string, or null. */
    fun value(word: String): Int? {
        if (word.isNotEmpty() && word.length <= 6 && word.all { it in '0'..'9' }) return word.toInt()
        return WORDS[word]
    }

    /** Reads a number at [at], possibly two words long; the value and how many tokens it took. */
    fun read(tokens: List<Token>, at: Int): Pair<Int, Int>? {
        val first = tokens.getOrNull(at)?.text ?: return null
        val value = value(first) ?: return null
        val tens = ITALIAN_TENS[first] ?: ENGLISH_TENS[first]
        if (tens != null) {
            val next = tokens.getOrNull(at + 1)?.text
            val unit = next?.let { ITALIAN_UNITS.indexOf(it).takeIf { i -> i >= 0 } ?: ENGLISH_UNITS.indexOf(it) }
            if (unit != null && unit in 1..9) return (tens + unit) to 2
        }
        return value to 1
    }
}
