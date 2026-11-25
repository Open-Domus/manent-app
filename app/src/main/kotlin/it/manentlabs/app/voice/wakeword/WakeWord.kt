// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.wakeword

/** Where a model comes from; the settings show it before anything is downloaded. */
enum class ModelOrigin {
    /** The openWakeWord project's own models: non-commercial use only (CC BY-NC-SA 4.0). */
    OPEN_WAKE_WORD,

    /** Home Assistant's community wake-word collection (MIT). */
    COMMUNITY,

    /** A file the owner imported on this phone. */
    IMPORTED,
}

/** A file published online, fetched on demand and checked against its SHA-256 before use. */
data class ModelSource(val url: String, val sha256: String)

/**
 * A word the phone can listen for: its id (stored in routes), the phrase
 * shown, and the classifier model file in the model folder.
 */
data class WakeWord(
    val id: String,
    val phrase: String,
    val fileName: String,
    val origin: ModelOrigin,
    val source: ModelSource? = null,
) {
    val imported: Boolean get() = origin == ModelOrigin.IMPORTED
}

/** The models the app knows how to fetch. Every word also needs the two [SHARED] front-end models. */
object WakeWordCatalogue {
    private const val RELEASE = "https://github.com/dscripka/openWakeWord/releases/download/v0.5.1/"
    private const val COMMUNITY =
        "https://raw.githubusercontent.com/fwartner/home-assistant-wakewords-collection/1c6d6a82947ff1367d8e5e50cc999c00943ccb9c/en/"

    const val MEL_FILE = "melspectrogram.onnx"
    const val EMBEDDING_FILE = "embedding_model.onnx"

    /** Front-end models every word shares, by file name. */
    val SHARED: Map<String, ModelSource> = linkedMapOf(
        MEL_FILE to ModelSource(RELEASE + MEL_FILE, "ba2b0e0f8b7b875369a2c89cb13360ff53bac436f2895cced9f479fa65eb176f"),
        EMBEDDING_FILE to ModelSource(RELEASE + EMBEDDING_FILE, "70d164290c1d095d1d4ee149bc5e00543250a7316b59f31d056cff7bd3075c1f"),
    )

    /** The words offered, in the order pickers show them. */
    val WORDS: List<WakeWord> = listOf(
        WakeWord(
            "ok_home", "ok home", "ok_home.onnx", ModelOrigin.COMMUNITY,
            ModelSource(COMMUNITY + "ok_home/ok_home.onnx", "93ec44a1b4ce76add6453d5cf52217b0496219dfaf425d376f7481495e005395"),
        ),
        openWakeWord("hey_jarvis", "hey jarvis", "94a13cfe60075b132f6a472e7e462e8123ee70861bc3fb58434a73712ee0d2cb"),
        openWakeWord("alexa", "alexa", "6ff566a01d12670e8d9e3c59da32651db1575d17272a601b7f8a39283dfbae3e"),
        openWakeWord("hey_mycroft", "hey mycroft", "c2a311e8fa1338de89c31b3b46dc4dffd4af2f9a8d6ddead48893c2d301b1f18"),
        openWakeWord("hey_rhasspy", "hey rhasspy", "5a9b3ed3be2910e35780e097905aa9f35a9c10038df47914cf2b3ec4d670f6ea"),
    )

    private fun openWakeWord(id: String, phrase: String, sha256: String): WakeWord {
        val file = "${id}_v0.1.onnx"
        return WakeWord(id, phrase, file, ModelOrigin.OPEN_WAKE_WORD, ModelSource(RELEASE + file, sha256))
    }
}
