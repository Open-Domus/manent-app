// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.phone

import it.manentlabs.app.voice.phone.NameMatching.ContactMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NameMatchingTest {
    private val apps = listOf("Fotocamera", "Google Foto", "Orologio", "Calcolatrice", "Maps", "YouTube Music", "YouTube")

    @Test
    fun `normalising drops case, accents and punctuation`() {
        assertEquals("fotocamera2", NameMatching.normalise("Fotocàmera 2"))
        assertEquals(listOf("maya", "reeves", "hall"), NameMatching.words("Maya  Reeves-Hall"))
    }

    @Test
    fun `an exact name wins`() {
        assertEquals(6, NameMatching.app("youtube", apps))
        assertEquals(0, NameMatching.app("Fotocamera", apps))
    }

    @Test
    fun `otherwise the shortest label containing the spoken name`() {
        assertEquals(3, NameMatching.app("calcola", apps))
        assertEquals(1, NameMatching.app("google", apps))
        assertNull(NameMatching.app("foto", apps))
        assertEquals(4, NameMatching.app("map", apps))
    }

    @Test
    fun `otherwise the longest label inside the spoken name`() {
        assertEquals(2, NameMatching.app("orologio della cucina", apps))
        assertEquals(5, NameMatching.app("youtube music player", apps))
    }

    @Test
    fun `short or unknown names match nothing`() {
        assertNull(NameMatching.app("ma", listOf("Mail", "Maps")))
        assertNull(NameMatching.app("telegram", apps))
        assertNull(NameMatching.app("", apps))
    }

    @Test
    fun `two labels equally short are not guessed between`() {
        assertNull(NameMatching.app("note", listOf("Note A", "Note B")))
    }

    @Test
    fun contacts() {
        val names = listOf("Maya Reeves", "Maya Lopez", "Luca Bianchi", "Luca")
        assertEquals(ContactMatch.One(0), NameMatching.contact("maya reeves", names))
        assertEquals(ContactMatch.One(3), NameMatching.contact("Luca", names))
        assertEquals(ContactMatch.One(2), NameMatching.contact("bianchi", names))
        assertEquals(ContactMatch.Several, NameMatching.contact("Maya", names))
        assertEquals(ContactMatch.None, NameMatching.contact("Giorgio", names))
        assertEquals(ContactMatch.Several, NameMatching.contact("anna", listOf("Anna", "ANNA")))
    }

    @Test
    fun `choosing the app for an action`() {
        assertEquals(AppPick.NoApp, AppPick.of("a", emptyList(), null))
        assertEquals(AppPick.Use("b"), AppPick.of("b", listOf("a", "b"), "a"))
        assertEquals(AppPick.Use("a"), AppPick.of("gone", listOf("a"), null))
        assertEquals(AppPick.Use("a"), AppPick.of(null, listOf("a", "b"), "a"))
        assertEquals(AppPick.MustChoose, AppPick.of(null, listOf("a", "b"), null))
        assertEquals(AppPick.MustChoose, AppPick.of(null, listOf("a", "b"), "android"))
    }
}
