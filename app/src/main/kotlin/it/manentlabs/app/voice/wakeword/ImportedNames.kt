// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.wakeword

/** How an imported model file is named on the phone and shown in the settings. */
object ImportedNames {
    /** Prefix of an imported word's id; the rest is its file name. */
    const val ID_PREFIX = "custom:"

    private val NOT_SAFE = Regex("[^a-z0-9]+")

    /** A trailing version such as " v0 1", which was "v0.1" before dots became underscores. */
    private val VERSION = Regex("\\s+v\\d+(\\s\\d+)*$")

    sealed interface Name {
        data class Ok(val fileName: String) : Name
        data object NotOnnx : Name
        data object Empty : Name
    }

    /** The file name an import of [displayName] is stored under: `Hey Nova v0.2.onnx` → `hey_nova_v0_2.onnx`. */
    fun fileName(displayName: String?): Name {
        val name = displayName?.trim().orEmpty()
        if (!name.lowercase().endsWith(".onnx")) return Name.NotOnnx
        val stem = name.dropLast(".onnx".length).lowercase().replace(NOT_SAFE, "_").trim('_')
        return if (stem.isEmpty()) Name.Empty else Name.Ok("$stem.onnx")
    }

    fun id(fileName: String): String = ID_PREFIX + fileName

    /** The phrase shown for a stored import: `hey_nova_v0_1.onnx` → "hey nova". */
    fun phrase(fileName: String): String {
        val spaced = fileName.removeSuffix(".onnx").replace('_', ' ')
        return spaced.replace(VERSION, "").trim().ifEmpty { spaced }
    }

    /** A word for an imported file. */
    fun word(fileName: String) = WakeWord(id(fileName), phrase(fileName), fileName, ModelOrigin.IMPORTED)
}
