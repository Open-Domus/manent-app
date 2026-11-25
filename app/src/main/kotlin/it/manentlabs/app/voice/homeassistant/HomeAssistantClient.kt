// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.homeassistant

import android.util.Log
import it.manentlabs.app.voice.CommandOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString

/**
 * Talks to Home Assistant over its WebSocket API: runs a command through
 * Assist, or tests a connection. Each call opens its own socket, waits at
 * most [timeoutMs] for the answer, closes it normally, and is never retried.
 */
class HomeAssistantClient(private val http: OkHttpClient, private val timeoutMs: Long = TIMEOUT_MS) {
    /** Sends [pcm] (16 kHz mono) to the Assist [pipeline], or the preferred one when null. */
    suspend fun run(baseUrl: String, token: String, pipeline: String?, pcm: ShortArray): CommandOutcome {
        val url = HomeAssistantAddress.webSocketUrl(baseUrl) ?: return CommandOutcome.Failed("unreachable")
        return converse(url, AssistRun(token, pipeline), pcm)
    }

    /** Signs in and lists the pipelines. */
    suspend fun test(baseUrl: String, token: String): ConnectionCheck {
        val url = HomeAssistantAddress.webSocketUrl(baseUrl) ?: return ConnectionCheck.Unreachable("unreachable")
        return converse(url, ConnectionTest(token), ShortArray(0))
    }

    private suspend fun <T> converse(url: String, conversation: HaConversation<T>, pcm: ShortArray): T {
        val answer = CompletableDeferred<T>()
        val lock = Any()

        fun perform(socket: WebSocket, steps: List<HaConversation.Step<T>>) {
            for (step in steps) {
                when (step) {
                    is HaConversation.Step.Send -> socket.send(step.text)
                    is HaConversation.Step.Audio -> AssistAudio.packets(step.handler, pcm).forEach { socket.send(it.toByteString()) }
                    is HaConversation.Step.Finish -> {
                        answer.complete(step.result)
                        socket.close(NORMAL_CLOSURE, null)
                    }
                }
            }
        }

        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val steps = synchronized(lock) {
                    if (answer.isCompleted) return
                    conversation.onText(text)
                }
                perform(webSocket, steps)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                lost()
                webSocket.close(NORMAL_CLOSURE, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.i(TAG, "Home Assistant socket failed: ${t::class.java.simpleName}")
                lost()
            }

            private fun lost() {
                synchronized(lock) { if (!answer.isCompleted) answer.complete(conversation.onClosed()) }
            }
        }

        val socket = try {
            http.newWebSocket(Request.Builder().url(url).build(), listener)
        } catch (e: IllegalArgumentException) {
            return conversation.onClosed()
        }
        try {
            return withTimeoutOrNull(timeoutMs) { answer.await() }
                ?: synchronized(lock) { conversation.onTimeout().also { answer.complete(it) } }
        } finally {
            socket.close(NORMAL_CLOSURE, null)
        }
    }

    companion object {
        const val TIMEOUT_MS = 60_000L
        private const val NORMAL_CLOSURE = 1000
        private const val TAG = "Manent:Assist"
    }
}
