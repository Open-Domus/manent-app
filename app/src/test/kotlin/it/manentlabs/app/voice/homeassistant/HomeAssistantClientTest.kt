// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.homeassistant

import it.manentlabs.app.voice.CommandOutcome
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** A small Home Assistant stand-in on MockWebServer, to run the client over a real socket. */
class HomeAssistantClientTest {
    private val server = MockWebServer()
    private val client = HomeAssistantClient(OkHttpClient(), timeoutMs = 2_000)
    private val texts = CopyOnWriteArrayList<String>()
    private val binary = CopyOnWriteArrayList<ByteString>()

    @After
    fun stop() = server.shutdown()

    private fun serve(onText: (WebSocket, String) -> Unit, greet: Boolean = true) {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (greet) webSocket.send("""{"type":"auth_required","ha_version":"2026.11.0"}""")
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    texts += text
                    onText(webSocket, text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    binary += bytes
                    if (bytes.size == 1) {
                        webSocket.send(
                            """[{"id":1,"type":"event","event":{"type":"stt-end","data":{}}},
                            {"id":1,"type":"event","event":{"type":"intent-end","data":{"intent_output":{"response":
                            {"response_type":"query_answer","speech":{"plain":{"speech":"Twenty degrees"}}}}}}}]""",
                        )
                    }
                }
            }),
        )
        server.start()
    }

    private val base get() = "http://${server.hostName}:${server.port}"

    @Test
    fun `a command runs through Assist and its answer comes back`() {
        serve({ socket, text ->
            when {
                "\"auth\"" in text -> socket.send("""{"type":"auth_ok","ha_version":"2026.11.0"}""")
                "assist_pipeline/run" in text -> {
                    socket.send("""{"id":1,"type":"result","success":true,"result":null}""")
                    socket.send("""{"id":1,"type":"event","event":{"type":"run-start","data":{"runner_data":{"stt_binary_handler_id":3}}}}""")
                }
            }
        })
        val outcome = runBlocking { client.run(base, "token", null, ShortArray(2_000)) }
        assertEquals(CommandOutcome.Done("Twenty degrees", answered = true), outcome)
        assertTrue(texts.first(), "\"access_token\":\"token\"" in texts.first())
        assertEquals(listOf(1 + 3_200, 1 + 800, 1), binary.map { it.size })
        assertTrue(binary.all { it[0] == 3.toByte() })
    }

    @Test
    fun `a bad token is reported`() {
        serve({ socket, _ -> socket.send("""{"type":"auth_invalid"}""") })
        assertEquals(ConnectionCheck.BadToken, runBlocking { client.test(base, "wrong") })
    }

    @Test
    fun `no answer in time is a timeout`() {
        serve({ socket, text -> if ("\"auth\"" in text) socket.send("""{"type":"auth_ok"}""") })
        assertEquals(CommandOutcome.Failed("timeout after sign-in"), runBlocking { client.run(base, "t", null, ShortArray(10)) })
    }

    @Test
    fun `a server that hangs up is closed`() {
        serve({ socket, _ -> socket.close(1000, null) })
        assertEquals(CommandOutcome.Failed("closed"), runBlocking { client.run(base, "t", null, ShortArray(10)) })
    }

    @Test
    fun `nothing listening is unreachable`() {
        server.start()
        val address = base
        server.shutdown()
        assertEquals(ConnectionCheck.Unreachable("unreachable"), runBlocking { client.test(address, "t") })
        assertEquals(ConnectionCheck.Unreachable("unreachable"), runBlocking { client.test("not an address", "t") })
    }
}
