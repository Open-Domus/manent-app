// SPDX-License-Identifier: MIT

package it.manentlabs.app.omnesis

import dev.omnesis.android.transport.http.GatewayHttp
import java.io.File
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class GatewayTranscriberTest {
    private val server = MockWebServer()
    private lateinit var wav: File

    @Before fun setUp() {
        server.start()
        wav = File.createTempFile("command", ".wav").apply { writeBytes(ByteArray(44)) }
    }

    @After fun tearDown() {
        server.shutdown()
        wav.delete()
    }

    private fun transcriber() =
        GatewayTranscriber(GatewayHttp(OkHttpClient(), server.url("/").toString(), "secret-token"))

    @Test fun postsTheAudioToTheInferenceRouteWithTheLanguage() = runTest {
        server.enqueue(MockResponse().setBody("""{"available":true,"text":"turn on the light","language":"en"}"""))

        assertEquals("turn on the light", transcriber().transcribe(wav, "audio/wav", "it-IT"))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/inference/transcribe?language=it", request.path)
        assertEquals("Bearer secret-token", request.getHeader("Authorization"))
        assertEquals("audio/wav", request.getHeader("Content-Type"))
        assertEquals(44L, request.bodySize)
    }

    @Test fun noTranscriberOnTheGatewayIsNoText() = runTest {
        server.enqueue(MockResponse().setBody("""{"available":false}"""))

        assertNull(transcriber().transcribe(wav, "audio/wav", null))
        assertEquals("/inference/transcribe", server.takeRequest().path)
    }
}
