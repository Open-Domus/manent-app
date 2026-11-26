// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.routes

import it.manentlabs.app.voice.homeassistant.HomeAssistantAddress
import it.manentlabs.app.voice.wakeword.ImportedNames
import it.manentlabs.app.voice.wakeword.WakeWordCatalogue
import it.manentlabs.app.voice.webhook.WebhookUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutesTest {
    private val routes = listOf(
        Route("home-assistant", "ok_home", Destination.HomeAssistant),
        Route("a1b2c3d4", "hey_jarvis", Destination.Webhook("https://n8n.example/hook")),
        Route("e5f6a7b8", "custom:hey_nova.onnx", Destination.Phone),
        Route("c9d0e1f2", "hey_mycroft", Destination.Hermes),
    )

    @Test
    fun `routes survive a round trip`() {
        assertEquals(routes, RouteCodec.decode(RouteCodec.encode(routes)))
    }

    @Test
    fun `the stored shape is the one earlier releases wrote`() {
        val stored = Json.parseToJsonElement(RouteCodec.encode(routes)).jsonArray
        val first = stored[0].jsonObject
        assertEquals("home-assistant", first["id"]!!.jsonPrimitive.content)
        assertEquals("ok_home", first["wakeWord"]!!.jsonPrimitive.content)
        assertEquals("home-assistant", first["destination"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(0.5, first["threshold"]!!.jsonPrimitive.content.toDouble(), 0.0)
        val webhook = stored[1].jsonObject["destination"]!!.jsonObject
        assertEquals("webhook", webhook["type"]!!.jsonPrimitive.content)
        assertEquals("https://n8n.example/hook", webhook["url"]!!.jsonPrimitive.content)
        assertEquals("phone", stored[2].jsonObject["destination"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("hermes", stored[3].jsonObject["destination"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `nothing stored or unreadable gives the defaults`() {
        val defaults = listOf(Route("home-assistant", "ok_home", Destination.HomeAssistant))
        assertEquals(defaults, RouteCodec.decode(null))
        assertEquals(defaults, RouteCodec.decode("not json"))
        assertEquals(defaults, RouteCodec.decode("""[{"id":"x"}]"""))
        assertEquals(emptyList<Route>(), RouteCodec.decode("[]"))
    }

    @Test
    fun `stored thresholds and unknown keys are read past`() {
        val stored = """[{"id":"r","wakeWord":"alexa","destination":{"type":"phone","extra":1},"threshold":0.8,"colour":"red"}]"""
        assertEquals(listOf(Route("r", "alexa", Destination.Phone)), RouteCodec.decode(stored))
    }

    @Test
    fun `a route to an unknown place is skipped and a webhook without url reads as empty`() {
        val stored = """[{"id":"a","wakeWord":"alexa","destination":{"type":"satellite"}},
            {"id":"b","wakeWord":"hey_jarvis","destination":{"type":"webhook"}}]"""
        assertEquals(listOf(Route("b", "hey_jarvis", Destination.Webhook(""))), RouteCodec.decode(stored))
    }

    @Test
    fun `new route ids are eight characters`() {
        assertEquals(8, Route.newId().length)
    }

    @Test
    fun `webhook addresses`() {
        assertTrue(WebhookUrl.isValid("https://automation.example.com/hook"))
        assertTrue(WebhookUrl.isValid("  http://192.168.1.10:5678/webhook/abc  "))
        assertFalse(WebhookUrl.isValid(""))
        assertFalse(WebhookUrl.isValid("https://"))
        assertFalse(WebhookUrl.isValid("https:///path"))
        assertFalse(WebhookUrl.isValid("ftp://example.com"))
        assertFalse(WebhookUrl.isValid("example.com/hook"))
        assertFalse(WebhookUrl.isValid("https://example.com/my hook"))
    }

    @Test
    fun `home assistant addresses become its websocket`() {
        assertEquals("ws://homeassistant.local:8123/api/websocket", HomeAssistantAddress.webSocketUrl("http://homeassistant.local:8123"))
        assertEquals("wss://ha.example.com/api/websocket", HomeAssistantAddress.webSocketUrl(" https://ha.example.com/ "))
        assertEquals("wss://ha.example.com/api/websocket", HomeAssistantAddress.webSocketUrl("HTTPS://ha.example.com"))
        assertNull(HomeAssistantAddress.webSocketUrl("homeassistant.local:8123"))
        assertNull(HomeAssistantAddress.webSocketUrl("http://"))
        assertNull(HomeAssistantAddress.webSocketUrl("ws://ha.local"))
    }

    @Test
    fun `route problems in order`() {
        val word = WakeWordCatalogue.WORDS.first()
        val ha = routes[0]
        val webhook = routes[1]
        assertEquals(RouteProblem.WORD_GONE, RouteStatus.problem(ha.copy(wakeWord = "old"), null, false, true, false))
        assertEquals(RouteProblem.IMPORT_MISSING, RouteStatus.problem(routes[2], null, false, true, false))
        assertEquals(RouteProblem.DOWNLOAD, RouteStatus.problem(ha, word, installed = false, homeAssistantReady = false, hermesReady = false))
        assertEquals(RouteProblem.HOME_ASSISTANT, RouteStatus.problem(ha, word, installed = true, homeAssistantReady = false, hermesReady = false))
        assertNull(RouteStatus.problem(ha, word, installed = true, homeAssistantReady = true, hermesReady = false))
        assertEquals(
            RouteProblem.WEBHOOK,
            RouteStatus.problem(webhook.copy(destination = Destination.Webhook("nope")), word, installed = true, homeAssistantReady = false, hermesReady = false),
        )
        assertNull(RouteStatus.problem(webhook, word, installed = true, homeAssistantReady = false, hermesReady = false))
        val hermes = Route("h", "alexa", Destination.Hermes)
        assertEquals(RouteProblem.HERMES, RouteStatus.problem(hermes, word, installed = true, homeAssistantReady = true, hermesReady = false))
        assertNull(RouteStatus.problem(hermes, word, installed = true, homeAssistantReady = false, hermesReady = true))
    }

    @Test
    fun `a phone route is active only with the overlay permission`() {
        val phone = routes[2]
        val word = ImportedNames.word("hey_nova.onnx")
        val problem = RouteStatus.problem(phone, word, installed = true, homeAssistantReady = false, hermesReady = false)
        assertNull(problem)
        assertFalse(RouteStatus.isActive(problem, phone, overlayAllowed = false))
        assertTrue(RouteStatus.isActive(problem, phone, overlayAllowed = true))
        assertTrue(RouteStatus.isActive(null, routes[1], overlayAllowed = false))
        assertFalse(RouteStatus.isActive(RouteProblem.DOWNLOAD, routes[1], overlayAllowed = true))
    }
}
