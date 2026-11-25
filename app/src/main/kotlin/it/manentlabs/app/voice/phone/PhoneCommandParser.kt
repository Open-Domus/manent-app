// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.phone

import java.time.LocalDate

/**
 * Turns a transcribed command, Italian or English, into a [PhoneAction] by
 * fixed rules, tried in a set order. The rules only act on what was clearly
 * said: a command they do not recognise gives null rather than a guess.
 *
 * Words are matched lower-case; what is passed on (a place, a name, a title)
 * keeps the transcript's own capitals.
 */
object PhoneCommandParser {
    fun parse(text: String, today: LocalDate): PhoneAction? {
        val command = Command.of(text) ?: return null
        val words = command.tokens.map { it.text }.toSet()
        return when {
            words.any { it in ALARM_WORDS } || ("wake" in words && "me" in words) -> alarm(command)
            "timer" in words -> Durations.timer(command.tokens)?.let(PhoneAction::Timer)
            else -> navigate(command)
                ?: call(command)
                ?: openApp(command)
                ?: calendar(command, today)
                ?: share(command)
                ?: unitTimer(command)
                ?: music(command)
                ?: Durations.timer(command.tokens)?.let(PhoneAction::Timer)
        }
    }

    private val ALARM_WORDS = setOf("sveglia", "svegliami", "allarme", "alarm")
    private val CALENDAR_WORDS = setOf(
        "appuntamento", "evento", "riunione", "incontro", "calendario", "visita",
        "appointment", "event", "meeting", "calendar",
    )

    private fun alarm(command: Command): PhoneAction? =
        TimesOfDay.any(command.tokens)?.let { PhoneAction.Alarm(it.time) }

    private val NAVIGATE = Regex(
        "^(?:naviga verso|naviga|navigare|portami|guidami|indicazioni|percorso|navigate|take me|drive me|drive|directions|get me)" +
            "(?=\\s|$)(?:\\s+(?:verso|fino a|fino|per|a|to|towards)(?=\\s|$))?" +
            "(?:\\s+(?:all'|l')|\\s+(?:alla|allo|agli|alle|ai|al|a|in|dalla|dallo|dal|dai|da|la|lo|il|the)(?=\\s|$))?\\s*",
    )

    private fun navigate(command: Command): PhoneAction? =
        command.after(NAVIGATE)?.let(PhoneAction::Navigate)

    private val CALL = Regex(
        "^(?:fai una telefonata a|fai una chiamata a|telefonare a|telefona a|telefonare|telefona|chiamami|chiama|call|phone|ring|dial)(?=\\s|$)\\s*",
    )

    private fun call(command: Command): PhoneAction? = command.after(CALL)?.let(PhoneAction::Call)

    private val OPEN = Regex("^(?:apri|avvia|lancia|open|launch|start)(?=\\s|$)\\s*")
    private val APP_WORDS = Regex("^(?:(?:l'applicazione|l'app|la app|the app|app|the|la|lo|il)(?=\\s|$)|l')\\s*")

    private fun openApp(command: Command): PhoneAction? {
        val rest = command.after(OPEN) ?: return null
        val name = rest.cutPrefix(APP_WORDS)
        return name.takeIf { it.isNotEmpty() }?.let(PhoneAction::OpenApp)
    }

    private val CALENDAR_VERB = Regex(
        "^(?:aggiungi|crea|metti|segna|fissa|add|create|schedule|put|set up)(?=\\s|$)(?:\\s+(?:un|una|uno|la|il|lo|a|an|the)(?=\\s|$)|\\s+(?:un'|l'))?",
    )
    private val CALENDAR_PLACE = Regex(
        "(?<=^|\\s)(?:(?:al|nel|in|sul)\\s+(?:mio\\s+)?calendario|(?:to|in|on)\\s+(?:(?:my|the)\\s+)?calendar)(?=\\s|$|[,.;:])",
    )

    private fun calendar(command: Command, today: LocalDate): PhoneAction? {
        if (command.tokens.none { it.text in CALENDAR_WORDS }) return null
        val day = Days.find(command.tokens, today)
        val found = TimesOfDay.anchored(command.tokens)
        if (day == null && found == null) return null
        var time = found?.time
        if (time != null && day?.tonight == true && time.hour in 1..11) time = time.copy(hour = time.hour + 12)

        val removed = BooleanArray(command.text.length)
        CALENDAR_VERB.find(command.lower)?.let { removed.fill(true, it.range.first, it.range.last + 1) }
        CALENDAR_PLACE.findAll(command.lower).forEach { removed.fill(true, it.range.first, it.range.last + 1) }
        day?.let { command.mark(removed, it.start, it.end) }
        found?.let { command.mark(removed, it.start, it.end) }
        val title = command.text.filterIndexed { i, _ -> !removed[i] }
            .replace(Regex("\\s+"), " ")
            .trim { it.isWhitespace() || it in ",;:-" }
            .replaceFirstChar { it.uppercaseChar() }
        return PhoneAction.Calendar(title.ifEmpty { null }, day?.date ?: today, time)
    }

    private val SHARE_RULES = listOf(
        Regex("^(?:aggiungi|metti|segna|scrivi|annota)\\s+(?:alla|nella|sulla)\\s+(?:mia\\s+)?lista(?:\\s+della\\s+spesa)?\\s*:?\\s+(.+)$"),
        Regex("^(?:aggiungi|metti|segna|scrivi)\\s+(.+?)\\s+(?:alla|nella|sulla)\\s+(?:mia\\s+)?lista(?:\\s+della\\s+spesa)?$"),
        Regex("^(?:add|put)\\s+(.+?)\\s+(?:to|on)\\s+(?:(?:my|the)\\s+)?(?:shopping\\s+)?list$"),
        Regex(
            "^(?:prendi nota|scrivi una nota|take a note|make a note|write down|annota|nota|note)" +
                "(?:\\s*:\\s*|\\s+(?:che|di|that|to)\\s+|\\s+)(.+)$",
        ),
    )

    private fun share(command: Command): PhoneAction? {
        for (rule in SHARE_RULES) {
            val match = rule.find(command.lower) ?: continue
            val range = match.groups[1]!!.range
            val text = command.text.substring(range.first, range.last + 1).trim()
            if (text.isNotEmpty()) return PhoneAction.Share(text)
        }
        return null
    }

    private fun unitTimer(command: Command): PhoneAction? {
        if (command.tokens.none { Durations.isUnit(it.text) }) return null
        return Durations.timer(command.tokens)?.let(PhoneAction::Timer)
    }

    private val MUSIC = Regex("^(?:fammi sentire|fai partire|riproduci|suona|metti|play|put on)(?=\\s|$)\\s*")
    private val MUSIC_WORDS = Regex("^(?:un po' di|un po di|la musica di|musica di|la canzone|music by|songs by|the song|some)(?=\\s|$)\\s*")

    private fun music(command: Command): PhoneAction? {
        val rest = command.after(MUSIC) ?: return null
        return rest.cutPrefix(MUSIC_WORDS).takeIf { it.isNotEmpty() }?.let(PhoneAction::Music)
    }

    /** The command tidied for matching: typographic apostrophes made plain, end punctuation dropped. */
    private class Command(val text: String) {
        val lower = Tokens.lower(text)
        val tokens = Tokens.of(lower)

        /** What follows a match of [prefix] at the start, in the original capitals; null when nothing does. */
        fun after(prefix: Regex): String? {
            val match = prefix.find(lower) ?: return null
            return text.substring(match.range.last + 1).trim().takeIf { it.isNotEmpty() }
        }

        /** Marks the characters of tokens [from] until [to] as removed, apostrophes between them included. */
        fun mark(removed: BooleanArray, from: Int, to: Int) {
            if (from >= to) return
            removed.fill(true, tokens[from].start, tokens[to - 1].end)
        }

        companion object {
            fun of(raw: String): Command? {
                val text = raw.replace('’', '\'').trim().trimEnd('.', '!', '?', ' ').trim()
                return if (text.isEmpty()) null else Command(text)
            }
        }
    }

    private fun String.cutPrefix(prefix: Regex): String {
        val match = prefix.find(Tokens.lower(this)) ?: return this
        return substring(match.range.last + 1).trim()
    }
}
