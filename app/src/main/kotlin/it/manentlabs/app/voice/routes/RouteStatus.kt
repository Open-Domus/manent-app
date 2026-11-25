// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.routes

import it.manentlabs.app.voice.wakeword.ImportedNames
import it.manentlabs.app.voice.wakeword.WakeWord
import it.manentlabs.app.voice.webhook.WebhookUrl

/** What keeps a route from working, in the order the settings report it. */
enum class RouteProblem {
    /** Its word is no longer one the app knows. */
    WORD_GONE,

    /** Its word was imported, and the file is gone. */
    IMPORT_MISSING,

    /** Its word's models have not been downloaded. */
    DOWNLOAD,

    /** It goes to Home Assistant, which is not set up. */
    HOME_ASSISTANT,

    /** It goes to a webhook without a usable address. */
    WEBHOOK,
}

object RouteStatus {
    /** The first problem with [route]; [word] is its wake word if the app still knows it. */
    fun problem(route: Route, word: WakeWord?, installed: Boolean, homeAssistantReady: Boolean): RouteProblem? = when {
        word == null && route.wakeWord.startsWith(ImportedNames.ID_PREFIX) -> RouteProblem.IMPORT_MISSING
        word == null -> RouteProblem.WORD_GONE
        !installed -> RouteProblem.DOWNLOAD
        route.destination == Destination.HomeAssistant && !homeAssistantReady -> RouteProblem.HOME_ASSISTANT
        route.destination is Destination.Webhook && !WebhookUrl.isValid(route.destination.url) -> RouteProblem.WEBHOOK
        else -> null
    }

    /**
     * Whether [route] can be listened for now: no problem, and for the phone,
     * permission to open apps from the background ("display over other apps").
     */
    fun isActive(problem: RouteProblem?, route: Route, overlayAllowed: Boolean): Boolean =
        problem == null && (route.destination != Destination.Phone || overlayAllowed)
}
