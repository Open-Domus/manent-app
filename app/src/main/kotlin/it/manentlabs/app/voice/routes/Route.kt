// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.routes

import java.util.UUID

/** Where the command after a wake word goes. */
sealed interface Destination {
    /** Home Assistant's Assist pipeline hears the audio itself. */
    data object HomeAssistant : Destination

    /** The transcript is posted as JSON to [url]. */
    data class Webhook(val url: String) : Destination

    /** The transcript is turned into an action on this phone. */
    data object Phone : Destination

    /** The transcript is sent to a Hermes Agent, whose reply is read aloud. */
    data object Hermes : Destination
}

/** One wake word and where its commands go. A word belongs to one route at most. */
data class Route(val id: String, val wakeWord: String, val destination: Destination) {
    companion object {
        /** A fresh route id: the first eight characters of a random UUID. */
        fun newId(): String = UUID.randomUUID().toString().take(8)

        /** What a new install starts with: "ok home" to Home Assistant. */
        val DEFAULTS = listOf(Route("home-assistant", "ok_home", Destination.HomeAssistant))
    }
}
