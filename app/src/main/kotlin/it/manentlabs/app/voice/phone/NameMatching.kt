// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.phone

import java.text.Normalizer

/** Compares spoken names with app labels and contact names, ignoring case, accents, spaces and punctuation. */
object NameMatching {
    private val MARKS = Regex("\\p{M}+")
    private val NOT_ALNUM = Regex("[^a-z0-9]+")

    /** `"Fotocàmera 2"` → `"fotocamera2"`. */
    fun normalise(name: String): String = squash(name, "")

    /** A name's words, each normalised: `"Maya  Reeves-Hall"` → `[maya, reeves, hall]`. */
    fun words(name: String): List<String> = squash(name, " ").split(' ').filter { it.isNotEmpty() }

    private fun squash(name: String, gap: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD).replace(MARKS, "").lowercase().replace(NOT_ALNUM, gap).trim()

    /**
     * The app among [labels] meant by [spoken]: the one whose label is exactly
     * the spoken name; else the shortest label containing it (at least three
     * letters spoken) when no other label is that short; else the longest
     * label (at least three letters) that the spoken words contain.
     * Returns its index, or null.
     */
    fun app(spoken: String, labels: List<String>): Int? {
        val said = normalise(spoken)
        if (said.isEmpty()) return null
        val names = labels.map(::normalise)
        names.indices.filter { names[it] == said }.singleOrNull()?.let { return it }
        if (said.length >= 3) {
            val containing = names.indices.filter { names[it].contains(said) }
            val shortest = containing.minOfOrNull { names[it].length }
            val best = containing.filter { names[it].length == shortest }
            if (best.size == 1) return best.single()
        }
        val contained = names.indices.filter { names[it].length >= 3 && said.contains(names[it]) }
        val longest = contained.maxOfOrNull { names[it].length } ?: return null
        return contained.firstOrNull { names[it].length == longest }
    }

    /** What a spoken name matches among contacts. */
    sealed interface ContactMatch {
        data class One(val index: Int) : ContactMatch
        data object None : ContactMatch
        data object Several : ContactMatch
    }

    /**
     * The contact meant by [spoken] among [names] (one per contact): an exact
     * full-name match wins; otherwise the contacts whose name has every spoken word.
     */
    fun contact(spoken: String, names: List<String>): ContactMatch {
        val said = normalise(spoken)
        if (said.isEmpty()) return ContactMatch.None
        val exact = names.indices.filter { normalise(names[it]) == said }
        if (exact.size == 1) return ContactMatch.One(exact.single())
        if (exact.size > 1) return ContactMatch.Several
        val spokenWords = words(spoken)
        val partial = names.indices.filter { i -> words(names[i]).let { have -> spokenWords.all { it in have } } }
        return when (partial.size) {
            0 -> ContactMatch.None
            1 -> ContactMatch.One(partial.single())
            else -> ContactMatch.Several
        }
    }

    private val DIALLABLE = Regex("^[0-9 +\\-().]+$")

    /** The number to dial when [spoken] is a phone number (digits and `+-().`, three digits at least), else null. */
    fun phoneNumber(spoken: String): String? {
        val text = spoken.trim()
        if (!DIALLABLE.matches(text) || text.count { it.isDigit() } < 3) return null
        return text.filter { it.isDigit() || it == '+' }
    }
}
