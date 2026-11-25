// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

import it.manentlabs.app.gateway.TlsFixtures.serve
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GatewayTest {
    private class MemoryStore : PairingStore {
        var pairing: Pairing? = null
        var id: String? = null
        override fun load() = pairing
        override fun save(pairing: Pairing) { this.pairing = pairing }
        override fun forget() { pairing = null }
        override fun installId() = id ?: "0123456789-install".also { id = it }
    }

    private class MemoryAvailability : AvailabilityMemory {
        var entry: Pair<String, RecordingAvailability>? = null
        override fun recall(pairingKey: String) = entry?.takeIf { it.first == pairingKey }?.second
        override fun remember(pairingKey: String, availability: RecordingAvailability) { entry = pairingKey to availability }
        override fun forget() { entry = null }
    }

    private val server = MockWebServer()
    private val certificate = TlsFixtures.selfSigned("gw.lan")
    private val store = MemoryStore()
    private val memory = MemoryAvailability()
    private val connector = GatewayConnector(TlsFixtures.baseClient, TlsFixtures.trust)
    private lateinit var url: String

    @Before
    fun start() {
        server.serve(certificate)
        server.start()
        url = server.url("/").toString()
    }

    @After
    fun stop() = server.shutdown()

    private fun gateway() = Gateway(store, memory, connector)

    private fun paired(token: String, tokenId: String? = "tid-$token", name: String = "Home") =
        MockResponse().setBody(
            """{"token":"$token",${tokenId?.let { "\"tokenId\":\"$it\"," } ?: ""}"device":{"id":"dev-$token","name":"$name","kind":"android"},"scopes":["read"]}""",
        )

    private fun Gateway.pairHere() = runBlocking { pairManually(url, "code", TlsFixtures.pinOf(certificate)) }

    @Test
    fun `starts unpaired with recording allowed`() {
        val gateway = gateway()
        assertNull(gateway.paired.value)
        assertNull(gateway.api.value)
        assertEquals(RecordingAvailability.AVAILABLE, gateway.recordingAvailability.value)
        assertEquals(TranscriberBlock.NOT_PAIRED, gateway.transcriberBlock.value)
    }

    @Test
    fun `pairing exchanges the code and stores the credential`() {
        server.enqueue(paired("t1"))
        val gateway = gateway()
        val result = gateway.pairHere()

        assertEquals(PairingResult.Paired(PairedGateway("Home", url)), result)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/devices/pair", request.path)
        assertNull(request.getHeader("Authorization"))
        assertNull(request.getHeader("Omnesis-Pairing-Generation"))
        assertEquals("application/json", request.getHeader("Accept"))
        assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
        val body = Json.parseToJsonElement(request.body.readUtf8()) as JsonObject
        assertEquals(
            Json.parseToJsonElement(
                """{"pairingCode":"code","capabilities":{"platform":"android","hostname":"Manent","suggestedName":"Manent-012345",""" +
                    """"installId":"0123456789-install","hostableSourceTypes":[],"pushBasedSourceTypes":[],"multiDeviceModes":{}}}""",
            ),
            body,
        )

        val stored = store.pairing!!
        assertEquals("t1", stored.token)
        assertEquals("tid-t1", stored.generation)
        assertEquals("dev-t1", stored.deviceId)
        assertEquals(TlsMode.PINNED_LEAF, stored.trust.mode)
        assertEquals("dev-t1", gateway.api.value!!.deviceId)
        assertEquals(RecordingAvailability.UNAVAILABLE, gateway.recordingAvailability.value)
        assertNull(gateway.transcriberBlock.value)
    }

    @Test
    fun `a new pairing names the device it replaces`() {
        server.enqueue(paired("t1"))
        server.enqueue(paired("t2", tokenId = null))
        val gateway = gateway()
        gateway.pairHere()
        gateway.pairHere()
        server.takeRequest()
        val capabilities = (Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject)["capabilities"]!!.jsonObject
        assertEquals("\"dev-t1\"", capabilities["previousDeviceId"].toString())
        assertNull(store.pairing!!.generation)
    }

    @Test
    fun `a refused code stores nothing`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"code expired"}"""))
        val result = gateway().pairHere()
        assertEquals("code expired", ((result as PairingResult.Failed).error as GatewayError.Forbidden).reason)
        assertNull(store.pairing)
    }

    @Test
    fun `a wrong pin fails as a network error`() {
        val result = runBlocking { gateway().pairManually(url, "code", "0".repeat(64)) }
        assertTrue((result as PairingResult.Failed).error is GatewayError.Network)
    }

    @Test
    fun `invalid input is reported without a request`() {
        val result = runBlocking { gateway().pairFromPayload("""{"v":1}""") }
        assertEquals(PairingResult.Invalid(PairingProblem.UnsupportedVersion("1")), result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a newer attempt supersedes one still running`() = runBlocking {
        server.enqueue(paired("old").setSocketPolicy(SocketPolicy.NO_RESPONSE))
        server.enqueue(paired("new"))
        val gateway = gateway()
        val pin = TlsFixtures.pinOf(certificate)

        val first = async(Dispatchers.IO) { gateway.pairManually(url, "a", pin) }
        withContext(Dispatchers.IO) { server.takeRequest() }
        val second = gateway.pairManually(url, "b", pin)

        assertEquals(PairingResult.Superseded, first.await())
        assertTrue(second is PairingResult.Paired)
        assertEquals("new", store.pairing!!.token)
    }

    @Test
    fun `unpairing supersedes a running attempt`() = runBlocking {
        server.enqueue(paired("late").setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val gateway = gateway()
        val attempt = async(Dispatchers.IO) { gateway.pairManually(url, "a", TlsFixtures.pinOf(certificate)) }
        withContext(Dispatchers.IO) { server.takeRequest() }
        gateway.unpair()
        assertEquals(PairingResult.Superseded, attempt.await())
        assertNull(store.pairing)
        assertNull(gateway.paired.value)
    }

    @Test
    fun `unpairing forgets the pairing but keeps the install id`() = runBlocking {
        server.enqueue(paired("t1"))
        val gateway = gateway()
        gateway.pairHere()
        gateway.unpair()
        assertNull(store.pairing)
        assertEquals("0123456789-install", store.id)
        assertNull(gateway.paired.value)
        assertNull(gateway.api.value)
        assertEquals(StatusCheck.Pending, gateway.status.value)
        assertEquals(RecordingAvailability.AVAILABLE, gateway.recordingAvailability.value)
        assertEquals(TranscriberBlock.NOT_PAIRED, gateway.transcriberBlock.value)
    }

    @Test
    fun `a status answer sets availability and is remembered for the pairing`() = runBlocking {
        server.enqueue(paired("t1"))
        server.enqueue(MockResponse().setBody("""{"experimental":true,"capabilities":{"recordings":{"min":1,"max":1}},"dictation":{"enabled":false}}"""))
        val gateway = gateway()
        gateway.pairHere()
        val status = gateway.refreshStatus()!!
        server.takeRequest()
        assertEquals("Bearer t1", server.takeRequest().getHeader("Authorization"))

        assertTrue(status.acceptsRecordings)
        assertEquals(RecordingAvailability.TRANSCRIPTION_OFF, gateway.recordingAvailability.value)
        assertEquals(TranscriberBlock.DICTATION_OFF, gateway.transcriberBlock.value)
        assertEquals(StatusLine.TranscriptionOff, StatusLine.of(gateway.status.value))

        // Next start, gateway out of reach: the remembered answer still holds.
        server.shutdown()
        val restarted = gateway()
        assertEquals(RecordingAvailability.TRANSCRIPTION_OFF, restarted.recordingAvailability.value)
        assertNull(restarted.refreshStatus())
        assertTrue((restarted.status.value as StatusCheck.Failed).error is GatewayError.Network)
        assertEquals(RecordingAvailability.TRANSCRIPTION_OFF, restarted.recordingAvailability.value)
    }

    @Test
    fun `transcription without a pairing gives nothing`() = runBlocking {
        assertNull(gateway().transcribe(java.io.File("missing.wav"), "en"))
        assertFalse(server.requestCount > 0)
    }
}
