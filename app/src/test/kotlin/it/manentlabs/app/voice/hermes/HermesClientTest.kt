// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.hermes

import it.manentlabs.app.voice.CommandOutcome
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HermesClientTest {
    private val server = MockWebServer()
    private val client = HermesClient(OkHttpClient())

    @After
    fun stop() = server.shutdown()

    private fun completion(content: String?, finish: String = "stop") =
        """{"object":"chat.completion","choices":[{"index":0,"message":{"role":"assistant","content":${content?.let { "\"$it\"" } ?: "null"}},"finish_reason":"$finish"}]}"""

    @Test
    fun `asks the agent and speaks its answer`() {
        server.enqueue(MockResponse().setBody(completion(" Domani piove. ")))
        val outcome = client.ask(server.url("/").toString(), "k3y", "che tempo fa domani", "it-IT")
        assertEquals(CommandOutcome.Done("Domani piove.", answered = true), outcome)
        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.path)
        assertEquals("Bearer k3y", request.getHeader("Authorization"))
        assertEquals(HermesClient.SESSION_KEY, request.getHeader("X-Hermes-Session-Key"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("hermes-agent", body["model"]!!.jsonPrimitive.content)
        val messages = body["messages"]!!.jsonArray
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("che tempo fa domani", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `agent failures and HTTP errors fail`() {
        assertEquals(CommandOutcome.Failed("agent error"), HermesClient.outcome(completion("x", "error")))
        assertEquals(CommandOutcome.Failed("agent ran out of steps"), HermesClient.outcome(completion(null, "length")))
        assertEquals(CommandOutcome.Failed("not a chat completion"), HermesClient.outcome("<html>"))
        assertEquals(CommandOutcome.Done(null, answered = true), HermesClient.outcome(completion("")))
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(CommandOutcome.Failed("HTTP 401"), client.ask(server.url("/").toString(), "k", "x", "en"))
        assertEquals(CommandOutcome.Failed("bad Hermes address"), client.ask("localhost:8642", "k", "x", "en"))
    }

    @Test
    fun `addresses with or without v1 or a profile`() {
        assertEquals("http://h:8642/v1/models", HermesClient.endpoint(" http://h:8642/ ", "models").toString())
        assertEquals("http://h:8642/v1/models", HermesClient.endpoint("http://h:8642/v1", "models").toString())
        assertEquals("https://h/p/work/v1/chat/completions", HermesClient.endpoint("https://h/p/work", "chat/completions").toString())
        assertNull(HermesClient.endpoint("h:8642", "models"))
    }

    @Test
    fun `the test lists the models with the key`() {
        server.enqueue(MockResponse().setBody("""{"object":"list","data":[{"id":"hermes-agent","object":"model"}]}"""))
        assertEquals(HermesCheck.Ok("hermes-agent"), client.test(server.url("/").toString(), "k"))
        assertEquals("/v1/models", server.takeRequest().path)
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(HermesCheck.BadKey, client.test(server.url("/").toString(), "bad"))
        server.enqueue(MockResponse().setBody("nope"))
        assertEquals(HermesCheck.Unreachable("not Hermes"), client.test(server.url("/").toString(), "k"))
    }
}
