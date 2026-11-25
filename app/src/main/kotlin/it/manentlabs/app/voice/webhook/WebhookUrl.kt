// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.webhook

/** Which webhook addresses are accepted. */
object WebhookUrl {
    /** `http://` or `https://`, then a host (not a bare path) and no spaces anywhere. */
    fun isValid(url: String): Boolean {
        val text = url.trim()
        val rest = when {
            text.startsWith("https://") -> text.removePrefix("https://")
            text.startsWith("http://") -> text.removePrefix("http://")
            else -> return false
        }
        return rest.isNotEmpty() && !rest.startsWith("/") && rest.none { it.isWhitespace() }
    }
}
