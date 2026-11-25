// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

/** The `language` hint for `POST /inference/transcribe`, taken from a locale tag. */
object TranscriptionLanguage {
    private val SUBTAG = Regex("^[a-z]{2,3}$")

    /** `it-IT` gives `it`; a tag whose first part is not 2 or 3 letters gives no hint. */
    fun of(localeTag: String?): String? {
        val first = localeTag?.split('-', '_')?.firstOrNull()?.trim()?.lowercase() ?: return null
        return first.takeIf { SUBTAG.matches(it) }
    }
}
