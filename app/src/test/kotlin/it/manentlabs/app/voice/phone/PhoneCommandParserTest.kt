// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.phone

import it.manentlabs.app.voice.phone.PhoneAction.Alarm
import it.manentlabs.app.voice.phone.PhoneAction.Calendar
import it.manentlabs.app.voice.phone.PhoneAction.Call
import it.manentlabs.app.voice.phone.PhoneAction.Music
import it.manentlabs.app.voice.phone.PhoneAction.Navigate
import it.manentlabs.app.voice.phone.PhoneAction.OpenApp
import it.manentlabs.app.voice.phone.PhoneAction.Share
import it.manentlabs.app.voice.phone.PhoneAction.Timer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PhoneCommandParserTest {
    /** A Wednesday. */
    private val today = LocalDate.of(2026, 11, 25)

    private fun parse(text: String) = PhoneCommandParser.parse(text, today)

    private fun at(hour: Int, minute: Int = 0) = ClockTime(hour, minute)

    @Test
    fun `timers in Italian`() {
        assertEquals(Timer(600), parse("timer di 10 minuti"))
        assertEquals(Timer(600), parse("Metti un timer di dieci minuti."))
        assertEquals(Timer(1800), parse("imposta un timer per mezz'ora"))
        assertEquals(Timer(1800), parse("timer di mezz’ora"))
        assertEquals(Timer(900), parse("timer di un quarto d'ora"))
        assertEquals(Timer(5400), parse("timer di un'ora e mezza"))
        assertEquals(Timer(90), parse("timer di un minuto e mezzo"))
        assertEquals(Timer(3600 + 600 + 30), parse("timer di 1 ora 10 minuti e 30 secondi"))
        assertEquals(Timer(25 * 60), parse("timer di venticinque minuti"))
        assertEquals(Timer(25 * 60), parse("timer di venti cinque minuti"))
        assertEquals(Timer(28 * 60), parse("timer ventotto minuti"))
        assertEquals(Timer(33), parse("timer di trentatré secondi"))
    }

    @Test
    fun `timers in English`() {
        assertEquals(Timer(300), parse("Set a timer for 5 minutes"))
        assertEquals(Timer(1800), parse("timer for half an hour"))
        assertEquals(Timer(900), parse("start a timer for a quarter of an hour"))
        assertEquals(Timer(60), parse("timer for a minute"))
        assertEquals(Timer(2 * 3600 + 25 * 60), parse("timer for two hours and twenty five minutes"))
        assertEquals(Timer(90 * 60), parse("timer for an hour and a half"))
        assertEquals(Timer(45), parse("timer 45 sec"))
    }

    @Test
    fun `a timer needs a length it can run`() {
        assertNull(parse("timer"))
        assertNull(parse("metti un timer"))
        assertNull(parse("timer di 25 ore"))
        assertNull(parse("timer di 0 minuti"))
    }

    @Test
    fun `bare lengths are timers`() {
        assertEquals(Timer(600), parse("10 minuti"))
        assertEquals(Timer(600), parse("metti 10 minuti"))
        assertEquals(Timer(1800), parse("mezz'ora"))
        assertEquals(Timer(180), parse("three minutes"))
    }

    @Test
    fun `alarms in Italian`() {
        assertEquals(Alarm(at(7, 30)), parse("sveglia alle 7:30"))
        assertEquals(Alarm(at(7, 30)), parse("sveglia alle 7.30"))
        assertEquals(Alarm(at(6, 45)), parse("svegliami alle sette meno un quarto"))
        assertEquals(Alarm(at(7, 45)), parse("sveglia alle 8 meno un quarto"))
        assertEquals(Alarm(at(7, 30)), parse("svegliami alle sette e mezza"))
        assertEquals(Alarm(at(6, 15)), parse("sveglia alle sei e un quarto"))
        assertEquals(Alarm(at(6, 20)), parse("sveglia alle 6 e 20"))
        assertEquals(Alarm(at(6, 50)), parse("sveglia alle sette meno dieci"))
        assertEquals(Alarm(at(1, 0)), parse("sveglia all'una"))
        assertEquals(Alarm(at(19, 0)), parse("allarme alle 7 di sera"))
        assertEquals(Alarm(at(15, 0)), parse("sveglia alle 3 del pomeriggio"))
        assertEquals(Alarm(at(7, 0)), parse("sveglia alle 7 di mattina"))
        assertEquals(Alarm(at(12, 0)), parse("sveglia a mezzogiorno"))
        assertEquals(Alarm(at(0, 0)), parse("sveglia a mezzanotte"))
        assertEquals(Alarm(at(6, 0)), parse("sveglia domani 6"))
        assertEquals(Alarm(at(23, 0)), parse("sveglia per le 23"))
        assertEquals(Alarm(at(21, 0)), parse("sveglia alle ventuno"))
    }

    @Test
    fun `alarms in English`() {
        assertEquals(Alarm(at(7, 0)), parse("Wake me up at 7"))
        assertEquals(Alarm(at(18, 0)), parse("set an alarm for 6 pm"))
        assertEquals(Alarm(at(6, 45)), parse("alarm at 6:45 a.m."))
        assertEquals(Alarm(at(0, 30)), parse("alarm at 12:30 am"))
        assertEquals(Alarm(at(19, 0)), parse("alarm at seven in the evening"))
        assertEquals(Alarm(at(19, 0)), parse("alarm at 7 tomorrow evening"))
        assertEquals(Alarm(at(7, 45)), parse("alarm at seven forty five"))
        assertEquals(Alarm(at(12, 0)), parse("alarm at noon"))
    }

    @Test
    fun `an alarm needs a time of day`() {
        assertNull(parse("sveglia"))
        assertNull(parse("set an alarm"))
        assertNull(parse("svegliami tra 10 minuti"))
        assertNull(parse("sveglia alle 25"))
        assertNull(parse("sveglia alle 7:75"))
    }

    @Test
    fun navigation() {
        assertEquals(Navigate("stazione"), parse("naviga verso la stazione"))
        assertEquals(Navigate("Milano"), parse("Portami a Milano"))
        assertEquals(Navigate("aeroporto"), parse("portami all'aeroporto"))
        assertEquals(Navigate("mare"), parse("guidami al mare"))
        assertEquals(Navigate("Piazza Duomo"), parse("indicazioni per Piazza Duomo"))
        assertEquals(Navigate("casa"), parse("naviga fino a casa"))
        assertEquals(Navigate("airport"), parse("Take me to the airport."))
        assertEquals(Navigate("Central Park"), parse("directions to Central Park"))
        assertEquals(Navigate("work"), parse("drive me to work"))
        assertEquals(Navigate("10 Downing Street"), parse("navigate to 10 Downing Street"))
        assertNull(parse("naviga"))
    }

    @Test
    fun calls() {
        assertEquals(Call("Maya Reeves"), parse("chiama Maya Reeves"))
        assertEquals(Call("mamma"), parse("telefona a mamma"))
        assertEquals(Call("Luca"), parse("fai una telefonata a Luca"))
        assertEquals(Call("333 123 4567"), parse("chiama 333 123 4567"))
        assertEquals(Call("+39 02-1234"), parse("call +39 02-1234"))
        assertEquals(Call("John"), parse("Call John."))
        assertEquals(Call("the office"), parse("phone the office"))
        assertNull(parse("chiama"))
    }

    @Test
    fun `calls tell numbers from names`() {
        assertEquals("3331234567", NameMatching.phoneNumber("333 123 4567"))
        assertEquals("+39021234", NameMatching.phoneNumber("+39 (02) 1234"))
        assertNull(NameMatching.phoneNumber("12"))
        assertNull(NameMatching.phoneNumber("Maya"))
        assertNull(NameMatching.phoneNumber("studio 3 4 5"))
    }

    @Test
    fun `opening apps`() {
        assertEquals(OpenApp("fotocamera"), parse("apri la fotocamera"))
        assertEquals(OpenApp("orologio"), parse("apri l'orologio"))
        assertEquals(OpenApp("Spotify"), parse("apri l'app Spotify"))
        assertEquals(OpenApp("Maps"), parse("avvia l'applicazione Maps"))
        assertEquals(OpenApp("calculator"), parse("open the calculator"))
        assertEquals(OpenApp("Gmail"), parse("launch the app Gmail"))
        assertNull(parse("apri"))
    }

    @Test
    fun `calendar events in Italian`() {
        assertEquals(
            Calendar("Appuntamento dal dentista", today.plusDays(1), at(15)),
            parse("appuntamento dal dentista domani alle 15"),
        )
        assertEquals(Calendar("Riunione", LocalDate.of(2026, 11, 30), at(10)), parse("riunione lunedì alle 10"))
        assertEquals(Calendar("Riunione", LocalDate.of(2026, 11, 30), at(10)), parse("riunione lunedi prossimo alle 10"))
        assertEquals(Calendar("Riunione di team", LocalDate.of(2026, 12, 2), null), parse("riunione di team mercoledì"))
        assertEquals(Calendar("Cena con Luca", today, at(20)), parse("aggiungi al calendario cena con Luca stasera alle 8"))
        assertEquals(Calendar("Visita medica", today.plusDays(2), at(9, 30)), parse("crea una visita medica dopodomani alle 9 e mezza"))
        assertEquals(Calendar("Evento", today, at(18)), parse("evento oggi alle 18"))
        assertEquals(Calendar("Compleanno di Sara", LocalDate.of(2026, 11, 28), null), parse("segna nel mio calendario compleanno di Sara sabato"))
    }

    @Test
    fun `calendar events in English`() {
        assertEquals(
            Calendar("Meeting with Anna", LocalDate.of(2026, 11, 27), at(15)),
            parse("add a meeting with Anna on Friday at 3 pm to my calendar"),
        )
        assertEquals(Calendar("Dentist appointment", today.plusDays(1), at(9)), parse("dentist appointment tomorrow at 9"))
        assertEquals(Calendar("Team meeting", today.plusDays(2), null), parse("schedule a team meeting the day after tomorrow"))
        assertEquals(Calendar("Event", today, at(19)), parse("event tonight at 7"))
        assertEquals(Calendar("Lunch meeting", today.plusDays(7), at(12)), parse("lunch meeting next wednesday at noon"))
        assertEquals(Calendar(null, today.plusDays(1), at(9)), parse("add to my calendar tomorrow at 9"))
    }

    @Test
    fun `a calendar word alone is not an event`() {
        assertNull(parse("riunione"))
        assertEquals(Share("riunione con il capo"), parse("prendi nota riunione con il capo"))
    }

    @Test
    fun `notes and lists`() {
        assertEquals(Share("latte"), parse("aggiungi alla lista latte"))
        assertEquals(Share("pane e burro"), parse("aggiungi alla lista della spesa pane e burro"))
        assertEquals(Share("il latte"), parse("metti il latte nella lista della spesa"))
        assertEquals(Share("uova"), parse("segna uova sulla mia lista"))
        assertEquals(Share("milk"), parse("add milk to my shopping list"))
        assertEquals(Share("eggs"), parse("put eggs on the list"))
        assertEquals(Share("domani chiamo il meccanico"), parse("prendi nota che domani chiamo il meccanico"))
        assertEquals(Share("comprare i fiori"), parse("nota: comprare i fiori"))
        assertEquals(Share("Buy flowers"), parse("take a note to Buy flowers"))
        assertEquals(Share("the code is 1234"), parse("write down the code is 1234"))
    }

    @Test
    fun music() {
        assertEquals(Music("jazz"), parse("metti un po' di jazz"))
        assertEquals(Music("jazz"), parse("metti un po’ di jazz"))
        assertEquals(Music("Vasco Rossi"), parse("riproduci la musica di Vasco Rossi"))
        assertEquals(Music("Bohemian Rhapsody"), parse("fammi sentire la canzone Bohemian Rhapsody"))
        assertEquals(Music("Miles Davis"), parse("play music by Miles Davis"))
        assertEquals(Music("jazz"), parse("put on some jazz"))
        assertNull(parse("metti"))
    }

    @Test
    fun `unknown commands give nothing`() {
        assertNull(parse(""))
        assertNull(parse("   "))
        assertNull(parse("che tempo fa"))
        assertNull(parse("what is the weather like?"))
        assertNull(parse("accendi la luce"))
    }
}
