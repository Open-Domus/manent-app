// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.wakeword

import it.manentlabs.app.voice.wakeword.ModelStore.ImportProblem
import it.manentlabs.app.voice.wakeword.ModelStore.ImportResult
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class ModelStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()

    @After
    fun stop() = server.shutdown()

    /** A client that sends every request to the mock server, keeping only the file name, so catalogue URLs can be served. */
    private fun clientServing(files: Map<String, ByteArray>, status: Int = 200): OkHttpClient {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val name = request.path!!.substringAfterLast('/')
                val body = files[name] ?: return MockResponse().setResponseCode(404)
                return MockResponse().setResponseCode(status).setBody(Buffer().write(body))
            }
        }
        server.start()
        return OkHttpClient.Builder().addInterceptor { chain ->
            val original = chain.request().url
            chain.proceed(chain.request().newBuilder().url(server.url("/" + original.pathSegments.last())).build())
        }.build()
    }

    private fun store(client: OkHttpClient = OkHttpClient()) = ModelStore(File(folder.root, "wakeword"), client)

    @Test
    fun `the catalogue keeps its order and published digests`() {
        assertEquals(listOf("ok_home", "hey_jarvis", "alexa", "hey_mycroft", "hey_rhasspy"), WakeWordCatalogue.WORDS.map { it.id })
        assertEquals(
            "https://github.com/dscripka/openWakeWord/releases/download/v0.5.1/hey_jarvis_v0.1.onnx",
            WakeWordCatalogue.WORDS[1].source!!.url,
        )
        assertEquals(ModelOrigin.COMMUNITY, WakeWordCatalogue.WORDS[0].origin)
        assertTrue(WakeWordCatalogue.WORDS.drop(1).all { it.origin == ModelOrigin.OPEN_WAKE_WORD })
        assertTrue((WakeWordCatalogue.SHARED.values + WakeWordCatalogue.WORDS.map { it.source!! }).all { it.sha256.length == 64 })
    }

    @Test
    fun `a download with the wrong digest is refused and nothing is kept`() {
        val store = store(clientServing(mapOf("melspectrogram.onnx" to byteArrayOf(1, 2, 3))))
        try {
            store.download(listOf(WakeWordCatalogue.WORDS[0]))
            fail("expected a digest failure")
        } catch (e: IOException) {
            assertTrue(e.message!!, e.message!!.startsWith("melspectrogram.onnx: unexpected digest 039058c6"))
        }
        assertFalse(store.sharedFile("melspectrogram.onnx").exists())
        assertFalse(File(folder.root, "wakeword/melspectrogram.onnx.part").exists())
        assertFalse(store.isInstalled(WakeWordCatalogue.WORDS[0]))
    }

    @Test
    fun `a missing file reports its HTTP status`() {
        val store = store(clientServing(emptyMap()))
        try {
            store.download(listOf(WakeWordCatalogue.WORDS[0]))
            fail("expected an HTTP failure")
        } catch (e: IOException) {
            assertEquals("melspectrogram.onnx: HTTP 404", e.message)
        }
    }

    @Test
    fun `nothing is fetched when everything is here`() {
        val store = store(OkHttpClient.Builder().addInterceptor { error("no network expected") }.build())
        val word = WakeWordCatalogue.WORDS[2]
        File(folder.root, "wakeword").mkdirs()
        listOf("melspectrogram.onnx", "embedding_model.onnx", word.fileName).forEach { File(folder.root, "wakeword/$it").writeText("x") }
        assertTrue(store.isInstalled(word))
        assertTrue(store.missing(listOf(word)).isEmpty())
        store.download(listOf(word))
    }

    @Test
    fun `imported file names`() {
        assertEquals(ImportedNames.Name.Ok("hey_nova_v0_2.onnx"), ImportedNames.fileName("Hey Nova v0.2.ONNX"))
        assertEquals(ImportedNames.Name.Ok("ciao_casa.onnx"), ImportedNames.fileName("  __Ciao, Casa!.onnx"))
        assertEquals(ImportedNames.Name.NotOnnx, ImportedNames.fileName("model.tflite"))
        assertEquals(ImportedNames.Name.NotOnnx, ImportedNames.fileName(null))
        assertEquals(ImportedNames.Name.Empty, ImportedNames.fileName("---.onnx"))
        assertEquals("hey nova", ImportedNames.phrase("hey_nova_v0_1.onnx"))
        assertEquals("hey nova", ImportedNames.phrase("hey_nova_v2.onnx"))
        assertEquals("ok computer", ImportedNames.phrase("ok_computer.onnx"))
        assertEquals("custom:hey_nova.onnx", ImportedNames.id("hey_nova.onnx"))
    }

    @Test
    fun `imports are checked before they are kept`() {
        val store = store()
        fun import(name: String?, bytes: ByteArray, size: Long? = bytes.size.toLong()) =
            (store.import(name, size) { bytes.inputStream() } as ImportResult.Refused).problem
        assertEquals(ImportProblem.NotOnnx, import("model.bin", byteArrayOf(1)))
        assertEquals(ImportProblem.NoName, import(".onnx", byteArrayOf(1)))
        assertEquals(ImportProblem.TooLarge, import("big.onnx", byteArrayOf(1), size = 21L * 1024 * 1024))
        assertEquals(ImportProblem.TooLarge, import("big.onnx", ByteArray(20 * 1024 * 1024 + 1), size = null))
        assertEquals(ImportProblem.NotClassifier, import("noise.onnx", ByteArray(64) { it.toByte() }))
        assertEquals(ImportProblem.CannotOpen, (store.import("x.onnx", 1) { null } as ImportResult.Refused).problem)
        assertEquals(WakeWordCatalogue.WORDS, store.words())
    }

    @Test
    fun `imported words follow the catalogue by file name, and forgetting removes them`() {
        val store = store()
        val custom = File(folder.root, "wakeword/custom").apply { mkdirs() }
        File(custom, "zeta.onnx").writeText("x")
        File(custom, "alpha_v0_1.onnx").writeText("x")
        val versionBefore = store.version.value
        assertEquals(listOf("custom:alpha_v0_1.onnx", "custom:zeta.onnx"), store.words().drop(WakeWordCatalogue.WORDS.size).map { it.id })
        assertEquals("alpha", store.word("custom:alpha_v0_1.onnx")!!.phrase)
        store.forget()
        assertFalse(custom.exists())
        assertEquals(WakeWordCatalogue.WORDS, store.words())
        assertTrue(store.version.value > versionBefore)
    }

    @Test
    fun `files whose digest matches are kept, and only missing ones are fetched`() {
        fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val files = mapOf("mel.onnx" to "mel".toByteArray(), "emb.onnx" to "emb".toByteArray(), "word.onnx" to "word".toByteArray())
        val client = clientServing(files)
        val shared = mapOf(
            "mel.onnx" to ModelSource("https://models.example/mel.onnx", digest(files.getValue("mel.onnx"))),
            "emb.onnx" to ModelSource("https://models.example/emb.onnx", digest(files.getValue("emb.onnx"))),
        )
        val word = WakeWord("w", "w", "word.onnx", ModelOrigin.COMMUNITY, ModelSource("https://models.example/word.onnx", digest(files.getValue("word.onnx"))))
        val store = ModelStore(File(folder.root, "wakeword"), client, shared)
        val versionBefore = store.version.value
        store.download(listOf(word))
        assertTrue(store.isInstalled(word))
        assertEquals("word", store.classifierFile(word).readText())
        assertEquals(3, server.requestCount)
        assertTrue(store.version.value > versionBefore)
        store.download(listOf(word))
        assertEquals(3, server.requestCount)
    }
}
