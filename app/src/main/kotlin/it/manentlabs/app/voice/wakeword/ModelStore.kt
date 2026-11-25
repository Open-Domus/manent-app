// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.wakeword

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * The wake-word models on this phone: downloaded ones in [root], imported
 * ones in `custom/` beneath it. Nothing is bundled with the app; each
 * download is checked against its published SHA-256 before it is kept.
 *
 * [version] moves on whenever the set of models changes, so listening can
 * start over with what is now installed.
 */
class ModelStore(
    private val root: File,
    private val http: OkHttpClient,
    private val shared: Map<String, ModelSource> = WakeWordCatalogue.SHARED,
) {
    private val custom = File(root, "custom")
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    /** The catalogue's words, then the imported ones by file name. */
    fun words(): List<WakeWord> = WakeWordCatalogue.WORDS + imported()

    fun word(id: String): WakeWord? = words().firstOrNull { it.id == id }

    fun classifierFile(word: WakeWord): File = if (word.imported) File(custom, word.fileName) else File(root, word.fileName)

    fun sharedFile(name: String): File = File(root, name)

    /** A word can be listened for once both shared models and its own are here. */
    fun isInstalled(word: WakeWord): Boolean =
        shared.keys.all { sharedFile(it).isFile } && classifierFile(word).isFile

    /** The catalogue words among [words] that still need downloading. */
    fun missing(words: Collection<WakeWord>): List<WakeWord> = words.filter { !it.imported && !isInstalled(it) }

    /**
     * Downloads what [words] lack: the shared models and the catalogue
     * classifiers not yet here. Stops at the first failure, keeping what
     * arrived before it; throws an [IOException] saying which file failed and how.
     */
    fun download(words: Collection<WakeWord>) {
        val wanted = buildList {
            shared.forEach { (name, source) -> add(sharedFile(name) to source) }
            words.forEach { word -> word.source?.let { add(classifierFile(word) to it) } }
        }.filter { (file, _) -> !file.isFile }.distinctBy { it.first }
        try {
            for ((file, source) in wanted) fetch(file, source)
        } finally {
            if (wanted.isNotEmpty()) _version.update { it + 1 }
        }
    }

    private fun fetch(target: File, source: ModelSource) {
        val request = Request.Builder().url(source.url).get().build()
        val bytes = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("${target.name}: HTTP ${response.code}")
            response.body?.bytes() ?: ByteArray(0)
        }
        if (bytes.isEmpty()) throw IOException("${target.name}: empty")
        val digest = sha256(bytes)
        if (!digest.equals(source.sha256, ignoreCase = true)) throw IOException("${target.name}: unexpected digest $digest")
        writeAtomically(target, bytes)
        Log.i(TAG, "Downloaded ${target.name}")
    }

    /** Why an import was refused. */
    sealed interface ImportProblem {
        data object NotOnnx : ImportProblem
        data object CannotOpen : ImportProblem
        data object NoName : ImportProblem
        data object TooLarge : ImportProblem
        data object NotClassifier : ImportProblem
        data class Exists(val phrase: String) : ImportProblem
        data class Failed(val message: String) : ImportProblem
    }

    sealed interface ImportResult {
        data class Imported(val word: WakeWord) : ImportResult
        data class Refused(val problem: ImportProblem) : ImportResult
    }

    /**
     * Imports an openWakeWord classifier the owner picked. [displayName] is the
     * file's name as the picker gives it, [size] its length if known, and
     * [open] reads it (null when it cannot be opened).
     */
    fun import(displayName: String?, size: Long?, open: () -> InputStream?): ImportResult {
        val fileName = when (val name = ImportedNames.fileName(displayName)) {
            ImportedNames.Name.NotOnnx -> return refused(ImportProblem.NotOnnx)
            ImportedNames.Name.Empty -> return refused(ImportProblem.NoName)
            is ImportedNames.Name.Ok -> name.fileName
        }
        if (size != null && size > MAX_IMPORT_BYTES) return refused(ImportProblem.TooLarge)
        val bytes = try {
            (open() ?: return refused(ImportProblem.CannotOpen)).use { readAtMost(it, MAX_IMPORT_BYTES) }
        } catch (e: IOException) {
            return refused(ImportProblem.Failed(e.message ?: e::class.java.simpleName))
        } ?: return refused(ImportProblem.TooLarge)
        if (!ClassifierCheck.runs(bytes)) return refused(ImportProblem.NotClassifier)
        val target = File(custom, fileName)
        if (target.exists()) return refused(ImportProblem.Exists(ImportedNames.phrase(fileName)))
        try {
            writeAtomically(target, bytes)
        } catch (e: IOException) {
            return refused(ImportProblem.Failed(e.message ?: e::class.java.simpleName))
        }
        _version.update { it + 1 }
        return ImportResult.Imported(ImportedNames.word(fileName))
    }

    /** Deletes every model, imported ones too. */
    fun forget() {
        root.deleteRecursively()
        _version.update { it + 1 }
    }

    private fun imported(): List<WakeWord> =
        custom.listFiles { file -> file.isFile && file.name.endsWith(".onnx") }
            .orEmpty()
            .sortedBy { it.name }
            .map { ImportedNames.word(it.name) }

    private fun refused(problem: ImportProblem) = ImportResult.Refused(problem)

    private fun writeAtomically(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, "${target.name}.part")
        part.writeBytes(bytes)
        if (!part.renameTo(target)) {
            part.delete()
            throw IOException("${target.name}: could not be saved")
        }
    }

    private companion object {
        const val TAG = "Manent:Assist"
        const val MAX_IMPORT_BYTES = 20L * 1024 * 1024

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        /** The whole stream, or null when it is longer than [limit]. */
        fun readAtMost(input: InputStream, limit: Long): ByteArray? {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) return out.toByteArray()
                out.write(buffer, 0, n)
                if (out.size() > limit) return null
            }
        }
    }
}
