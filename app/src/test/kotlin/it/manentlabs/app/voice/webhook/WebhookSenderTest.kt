// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.webhook

import it.manentlabs.app.voice.CommandOutcome
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class WebhookSenderTest {
    private val server = MockWebServer()
    private val sender = WebhookSender(OkHttpClient())
    private val at = Instant.parse("2026-11-25T10:15:30.123456Z")

    @After
    fun stop() = server.shutdown()

    @Test
    fun `posts the command as JSON with the bearer secret`() {
        server.enqueue(MockResponse().setResponseCode(204))
        val outcome = sender.send(" ${server.url("/hook")} ", "s3cret", "accendi la luce", "custom:foo.onnx", "it-IT", at)
        assertEquals(CommandOutcome.Done(), outcome)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/hook", request.path)
        assertEquals("Bearer s3cret", request.getHeader("Authorization"))
        assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("accendi la luce", body["text"]!!.jsonPrimitive.content)
        assertEquals("custom:foo.onnx", body["wakeWord"]!!.jsonPrimitive.content)
        assertEquals("it-IT", body["locale"]!!.jsonPrimitive.content)
        assertEquals("2026-11-25T10:15:30.123Z", body["at"]!!.jsonPrimitive.content)
    }

    @Test
    fun `no secret, no authorization header`() {
        server.enqueue(MockResponse())
        sender.send(server.url("/hook").toString(), null, "x", "ok_home", "en-US", at)
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `errors and bad addresses fail`() {
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(CommandOutcome.Failed("HTTP 500"), sender.send(server.url("/hook").toString(), null, "x", "ok_home", "en", at))
        assertEquals(CommandOutcome.Failed("no webhook address"), sender.send("  ", null, "x", "ok_home", "en", at))
        assertEquals(CommandOutcome.Failed("bad webhook address"), sender.send("example.com/hook", null, "x", "ok_home", "en", at))
        server.shutdown()
        assertEquals(CommandOutcome.Failed("unreachable"), sender.send(server.url("/hook").toString(), null, "x", "ok_home", "en", at))
    }
}
