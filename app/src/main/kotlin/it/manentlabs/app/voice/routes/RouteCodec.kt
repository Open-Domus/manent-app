// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.routes

import it.manentlabs.app.voice.capture.CapturePolicy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Routes as stored in the `routes` preference, in the JSON earlier releases
 * wrote: `[{"id":…,"wakeWord":…,"destination":{"type":"webhook","url":…},"threshold":0.5}]`.
 *
 * The stored threshold is read past: every word fires at the same
 * [CapturePolicy.WAKE_THRESHOLD], which is also what is written back.
 */
object RouteCodec {
    private const val HOME_ASSISTANT = "home-assistant"
    private const val WEBHOOK = "webhook"
    private const val PHONE = "phone"

    @Serializable
    private data class StoredDestination(val type: String, val url: String? = null)

    @Serializable
    private data class StoredRoute(
        val id: String,
        val wakeWord: String,
        val destination: StoredDestination,
        val threshold: Float = CapturePolicy.WAKE_THRESHOLD,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val serializer = ListSerializer(StoredRoute.serializer())

    /** The stored routes; the defaults when nothing is stored or it cannot be read. A route to an unknown place is skipped. */
    fun decode(stored: String?): List<Route> {
        if (stored == null) return Route.DEFAULTS
        val routes = try {
            json.decodeFromString(serializer, stored)
        } catch (_: SerializationException) {
            return Route.DEFAULTS
        } catch (_: IllegalArgumentException) {
            return Route.DEFAULTS
        }
        return routes.mapNotNull { stored ->
            val destination = when (stored.destination.type) {
                HOME_ASSISTANT -> Destination.HomeAssistant
                WEBHOOK -> Destination.Webhook(stored.destination.url.orEmpty())
                PHONE -> Destination.Phone
                else -> return@mapNotNull null
            }
            Route(stored.id, stored.wakeWord, destination)
        }
    }

    fun encode(routes: List<Route>): String = json.encodeToString(
        serializer,
        routes.map { route ->
            val destination = when (val d = route.destination) {
                Destination.HomeAssistant -> StoredDestination(HOME_ASSISTANT)
                is Destination.Webhook -> StoredDestination(WEBHOOK, d.url)
                Destination.Phone -> StoredDestination(PHONE)
            }
            StoredRoute(route.id, route.wakeWord, destination)
        },
    )
}
