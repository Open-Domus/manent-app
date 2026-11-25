// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.homeassistant

/** Home Assistant's address as the owner types it, and the WebSocket API behind it. */
object HomeAssistantAddress {
    /** `https://ha.example:8123/` → `wss://ha.example:8123/api/websocket`; null when it is not an http(s) address. */
    fun webSocketUrl(base: String): String? {
        val text = base.trim().trimEnd('/')
        val (scheme, rest) = when {
            text.startsWith("https://", ignoreCase = true) -> "wss://" to text.substring("https://".length)
            text.startsWith("http://", ignoreCase = true) -> "ws://" to text.substring("http://".length)
            else -> return null
        }
        if (rest.isEmpty() || rest.startsWith("/") || rest.any { it.isWhitespace() }) return null
        return "$scheme$rest/api/websocket"
    }
}
