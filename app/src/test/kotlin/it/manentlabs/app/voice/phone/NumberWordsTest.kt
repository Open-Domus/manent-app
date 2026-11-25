// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberWordsTest {
    private fun read(text: String) = NumberWords.read(Tokens.of(Tokens.lower(text)), 0)

    @Test
    fun `single words`() {
        val expected = mapOf(
            "0" to 0, "7" to 7, "45" to 45, "zero" to 0, "uno" to 1, "un" to 1, "una" to 1, "a" to 1, "an" to 1,
            "tre" to 3, "dieci" to 10, "quattordici" to 14, "diciannove" to 19, "venti" to 20,
            "ventuno" to 21, "ventitré" to 23, "ventitre" to 23, "ventotto" to 28, "trentatré" to 33, "trentuno" to 31,
            "quarantotto" to 48, "cinquantanove" to 59, "sessanta" to 60, "sessantanove" to 69,
            "one" to 1, "twelve" to 12, "nineteen" to 19, "twenty" to 20, "forty" to 40, "sixty" to 60,
        )
        for ((word, value) in expected) assertEquals(word, value, NumberWords.value(word))
    }

    @Test
    fun `two words`() {
        assertEquals(25 to 2, read("venti cinque"))
        assertEquals(25 to 2, read("twenty five"))
        assertEquals(41 to 2, read("forty one"))
        assertEquals(20 to 1, read("venti minuti"))
        assertEquals(30 to 1, read("thirty"))
    }

    @Test
    fun `not numbers`() {
        assertNull(NumberWords.value("minuti"))
        assertNull(NumberWords.value("ventinovanta"))
        assertNull(NumberWords.value("1234567"))
        assertNull(read("casa"))
    }

    @Test
    fun `tokens keep clock times whole and split digits from letters`() {
        assertEquals(listOf("alle", "7:30"), Tokens.of("alle 7:30").map { it.text })
        assertEquals(listOf("7", "am"), Tokens.of("7am").map { it.text })
        assertEquals(listOf("mezz", "ora"), Tokens.of("mezz'ora").map { it.text })
    }
}
