// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class GatewayApiTest {
    @get:Rule
    val files = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var api: GatewayApi

    @Before
    fun start() {
        server.start()
        val client = OkHttpClient()
        api = GatewayApi(server.url("/gw/"), "tok", "gen-1", "dev-1", GatewayClients(client, client, client))
    }

    @After
    fun stop() = server.shutdown()

    private fun reply(code: Int = 200, body: String = "{}") = server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    private fun <T> call(block: suspend GatewayApi.() -> T): T = runBlocking { api.block() }

    private inline fun <reified E : GatewayError> failure(noinline block: suspend GatewayApi.() -> Unit): E =
        try {
            call(block)
            throw AssertionError("expected ${E::class.simpleName}")
        } catch (e: GatewayError) {
            e as? E ?: throw AssertionError("got ${e::class.simpleName}", e)
        }

    private fun json(request: RecordedRequest) = Json.parseToJsonElement(request.body.readUtf8()) as JsonObject

    @Test
    fun `every request carries the auth headers`() {
        reply(body = """{"experimental":true}""")
        call { status() }
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/gw/status", request.path)
        assertEquals("application/json", request.getHeader("Accept"))
        assertEquals("Bearer tok", request.getHeader("Authorization"))
        assertEquals("gen-1", request.getHeader("Omnesis-Pairing-Generation"))
    }

    @Test
    fun `no generation header without a generation`() {
        val client = OkHttpClient()
        api = GatewayApi(server.url("/"), "tok", null, "dev-1", GatewayClients(client, client, client))
        reply()
        call { deleteRecording("r") }
        val request = server.takeRequest()
        assertNull(request.getHeader("Omnesis-Pairing-Generation"))
        assertEquals("DELETE", request.method)
        assertEquals("/recordings/r", request.path)
    }

    @Test
    fun `ids are one path segment, encoded once`() {
        reply(body = """{"speakers":[]}""")
        call { assignSpeaker("a/b c%", 2, SpeakerAssignment.Clear) }
        assertEquals("/gw/recordings/a%2Fb%20c%25/speakers/2", server.takeRequest().path)
    }

    @Test
    fun `start sends the session with the device id and leaves nulls out`() {
        reply(201, "{}")
        call { startRecording(RecordingStart(id = "r1", startedAt = Instant.parse("2026-01-02T03:04:05Z"), ambient = true)) }
        val request = server.takeRequest()
        assertEquals("/gw/recordings", request.path)
        assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
        val body = json(request)
        assertEquals(setOf("id", "origin", "startedAt", "deviceId"), body.keys)
        assertEquals("ambient", body["origin"]!!.jsonPrimitive.content)
        assertEquals("2026-01-02T03:04:05Z", body["startedAt"]!!.jsonPrimitive.content)
        assertEquals("dev-1", body["deviceId"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a 2xx start or finish is enough whatever the body`() {
        reply(200, "not json")
        reply(200, "")
        call { startRecording(RecordingStart(id = "r1", startedAt = Instant.EPOCH)) }
        call { finishRecording("r1", 3, Instant.EPOCH) }
        server.takeRequest()
        val finish = server.takeRequest()
        assertEquals("/gw/recordings/r1/finish", finish.path)
        assertEquals("""{"chunkCount":3,"endedAt":"1970-01-01T00:00:00Z"}""", finish.body.readUtf8())
    }

    @Test
    fun `a chunk goes as multipart with its audio`() {
        val audio = files.newFile("part-0.m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        reply(body = """{"seq":0,"status":"stored"}""")
        call { uploadChunk("r1", 0, ChunkUpload(Instant.EPOCH, 1500, audio = audio, mimeType = "audio/mp4")) }
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/gw/recordings/r1/chunks/0", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("""name="chunk""""))
        assertTrue(body.contains("""{"startedAt":"1970-01-01T00:00:00Z","durationMs":1500}"""))
        assertTrue(body.contains("""name="audio"; filename="part-0.m4a""""))
        assertTrue(body.contains("Content-Type: audio/mp4"))
    }

    @Test
    fun `a silent chunk has no audio part`() {
        reply()
        call { uploadChunk("r1", 4, ChunkUpload(Instant.EPOCH, 900, silent = true, audio = files.newFile("x.m4a"))) }
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains(""""silent":true"""))
        assertFalse(body.contains("interrupted"))
        assertFalse(body.contains("name=\"audio\""))
    }

    @Test
    fun `a chunk whose audio vanished is sent as an interrupted gap`() {
        reply()
        call { uploadChunk("r1", 1, ChunkUpload(Instant.EPOCH, 900, audio = files.root.resolve("gone.m4a"))) }
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains(""""silent":true,"interrupted":true"""))
        assertFalse(body.contains("name=\"audio\""))
    }

    @Test
    fun `recent recordings and speakers are not cached and decode with defaults`() {
        reply(body = """{"recordings":[{"id":"r","startedAt":"2026-01-01T00:00:00Z","state":"queued","extra":1}]}""")
        reply(body = """{"speakers":[{"speaker":1,"name":"Ada"},{"speaker":2}]}""")
        val rows = call { recentRecordings() }
        val speakers = call { speakers("r") }
        assertEquals(listOf(GatewayRecording("r", "", "2026-01-01T00:00:00Z", "queued", 0, 0)), rows)
        assertEquals(listOf(true, false), speakers.map { it.isNamed })
        val list = server.takeRequest()
        assertEquals("/gw/recordings?limit=20", list.path)
        assertEquals("no-store", list.getHeader("Cache-Control"))
        assertEquals("no-store", server.takeRequest().getHeader("Cache-Control"))
    }

    @Test
    fun `speaker assignments send exactly one field`() {
        val bodies = listOf(
            SpeakerAssignment.Me to """{"self":true}""",
            SpeakerAssignment.Person("p1") to """{"personId":"p1"}""",
            SpeakerAssignment.Name("Ada") to """{"name":"Ada"}""",
            SpeakerAssignment.Clear to """{"clear":true}""",
        )
        for ((assignment, expected) in bodies) {
            reply(body = """{"speakers":[]}""")
            call { assignSpeaker("r", 1, assignment) }
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals(expected, request.body.readUtf8())
        }
    }

    @Test
    fun `people search trims the query and drops it when blank`() {
        reply(body = """{"items":[{"id":"p","canonicalName":"Ada","isSelf":false,"aliases":[]}],"pageInfo":{}}""")
        reply(body = """{"items":[]}""")
        assertEquals(listOf(GatewayPerson("p", "Ada", false)), call { searchPeople("  ada lovelace ") })
        call { searchPeople("  ") }
        assertEquals("/gw/people?q=ada%20lovelace&limit=20", server.takeRequest().path)
        assertEquals("/gw/people?limit=20", server.takeRequest().path)
    }

    @Test
    fun `a voice note carries its metadata and audio`() {
        val audio = files.newFile("clip.m4a").apply { writeBytes(byteArrayOf(9)) }
        reply(202, """{"id":"n","transcription":"pending"}""")
        val at = Instant.parse("2026-07-01T10:00:00Z")
        call { sendVoiceNote(VoiceNoteUpload(at, "rec-9", audio), Locale.ITALY, ZoneId.of("Europe/Rome")) }
        val request = server.takeRequest()
        assertEquals("/gw/notes/voice", request.path)
        val body = request.body.readUtf8()
        val note = Json.parseToJsonElement(Regex("""name="note"\r\n.*?\r\n\r\n(\{.*?\})\r\n""", RegexOption.DOT_MATCHES_ALL)
            .find(body)!!.groupValues[1]) as JsonObject
        assertEquals("", note["text"]!!.jsonPrimitive.content)
        assertEquals("android-marked-moment", note["surface"]!!.jsonPrimitive.content)
        assertEquals("dev-1", note["deviceId"]!!.jsonPrimitive.content)
        assertEquals("it-IT", note["language"]!!.jsonPrimitive.content)
        assertEquals("Europe/Rome", note["capturedTimeZoneId"]!!.jsonPrimitive.content)
        assertEquals("7200", note["capturedUtcOffsetSeconds"]!!.jsonPrimitive.content)
        assertEquals("rec-9", note["recordingId"]!!.jsonPrimitive.content)
        assertFalse("taskId" in note)
        assertTrue(body.contains("""name="audio"; filename="clip.m4a""""))
        assertTrue(body.contains("Content-Type: audio/mp4"))
    }

    @Test
    fun `transcription posts raw wav with a language hint`() {
        val wav = files.newFile("command.wav").apply { writeBytes(byteArrayOf(0x52, 0x49)) }
        reply(body = """{"available":true,"text":"turn on the light"}""")
        reply(body = """{"available":false,"text":"ignored"}""")
        assertEquals("turn on the light", call { transcribe(wav, "it-IT") })
        assertNull(call { transcribe(wav, "x") })
        val request = server.takeRequest()
        assertEquals("/gw/inference/transcribe?language=it", request.path)
        assertEquals("audio/wav", request.getHeader("Content-Type"))
        assertEquals(2L, request.bodySize)
        assertEquals("/gw/inference/transcribe", server.takeRequest().path)
    }

    @Test
    fun `errors are classified by status`() {
        reply(401, """{"error":"bad token"}""")
        failure<GatewayError.Unauthorized> { status() }.also { assertEquals("unauthorized", it.message) }

        reply(403, """{"error":"missing scope","code":"SCOPE"}""")
        failure<GatewayError.Forbidden> { status() }.also {
            assertEquals("missing scope", it.reason)
            assertEquals("SCOPE", it.code)
        }

        reply(404, "")
        failure<GatewayError.NotFound> { status() }.also {
            assertNull(it.reason)
            assertEquals("not found", it.message)
        }

        reply(409, """{"error":" transcription is off ","code":"DICTATION_DISABLED"}""")
        failure<GatewayError.Server> { status() }.also {
            assertEquals(409, it.status)
            assertEquals("transcription is off", it.body)
            assertEquals(GatewayError.DICTATION_DISABLED, it.code)
        }

        reply(502, "<html>bad gateway</html>")
        failure<GatewayError.Server> { status() }.also {
            assertEquals("<html>bad gateway</html>", it.body)
            assertNull(it.code)
        }

        reply(500, """{"error":"  ","code":"X"}""")
        failure<GatewayError.Server> { status() }.also {
            assertEquals("""{"error":"  ","code":"X"}""", it.body)
            assertNull(it.code)
        }

        reply(200, "[]")
        failure<GatewayError.Decoding> { status() }

        reply(200, """{"recordings":[{"id":"r"}]}""")
        failure<GatewayError.Decoding> { recentRecordings() }
    }

    @Test
    fun `an unreachable gateway is a network error`() {
        server.shutdown()
        val error = failure<GatewayError.Network> { status() }
        assertTrue(error.cause is IOException)
    }
}
