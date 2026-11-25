// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.wakeword

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer

/** Scores 80 ms frames of 16 kHz audio for each of a set of wake words. */
interface WakeWordScorer : Closeable {
    /** One score, 0 to 1, per word for [frame] ([WakeWordEngine.FRAME_SAMPLES] samples). Throws if a model fails. */
    fun score(frame: ShortArray): FloatArray

    /** Forgets earlier audio, for audio that does not follow on from it. */
    fun reset()
}

/**
 * openWakeWord's streaming pipeline on ONNX Runtime, one thread.
 *
 * Each frame, with the last 480 samples of the frame before it, becomes rows
 * of a 32-band mel spectrogram; the last 76 rows become one 96-value
 * embedding; each word's classifier scores the last 16 embeddings (1.28 s).
 * The first two stages are shared, so an extra word costs one small model
 * run per frame. Until 16 embeddings of real audio exist every score is 0,
 * so the padding the windows start with cannot fire a word.
 *
 * The constructor loads every model and throws if one does not load.
 */
class WakeWordEngine(mel: File, embedding: File, classifiers: List<File>) : WakeWordScorer {
    private val env = OrtEnvironment.getEnvironment()
    private val sessions = mutableListOf<OrtSession>()
    private val melSession = open(mel)
    private val embeddingSession = open(embedding)
    private val classifierSessions = classifiers.map(::open)

    private val tail = FloatArray(CONTEXT_SAMPLES)
    private val melRows = RowWindow(EMBEDDING_ROWS, MEL_BANDS, MEL_FILL)
    private val embeddings = RowWindow(CLASSIFIER_FRAMES, EMBEDDING_WIDTH, 0f)
    private var embeddingsSeen = 0

    override fun score(frame: ShortArray): FloatArray {
        require(frame.size == FRAME_SAMPLES) { "A frame is $FRAME_SAMPLES samples" }
        val audio = FloatArray(CONTEXT_SAMPLES + FRAME_SAMPLES)
        tail.copyInto(audio)
        for (i in frame.indices) audio[CONTEXT_SAMPLES + i] = frame[i].toFloat()
        audio.copyInto(tail, 0, audio.size - CONTEXT_SAMPLES)

        val spectrum = run(melSession, audio, longArrayOf(1, audio.size.toLong()))
        for (i in spectrum.indices) spectrum[i] = spectrum[i] / 10f + 2f
        melRows.push(spectrum.copyOf(spectrum.size - spectrum.size % MEL_BANDS))

        val embedding = run(embeddingSession, melRows.snapshot(), longArrayOf(1, EMBEDDING_ROWS.toLong(), MEL_BANDS.toLong(), 1))
        embeddings.push(embedding.copyOf(EMBEDDING_WIDTH))
        if (embeddingsSeen < CLASSIFIER_FRAMES) embeddingsSeen++
        if (embeddingsSeen < CLASSIFIER_FRAMES) return FloatArray(classifierSessions.size)

        val window = embeddings.snapshot()
        val shape = longArrayOf(1, CLASSIFIER_FRAMES.toLong(), EMBEDDING_WIDTH.toLong())
        return FloatArray(classifierSessions.size) { run(classifierSessions[it], window, shape)[0] }
    }

    override fun reset() {
        tail.fill(0f)
        melRows.reset()
        embeddings.reset()
        embeddingsSeen = 0
    }

    override fun close() = sessions.forEach { it.close() }

    private fun open(file: File): OrtSession {
        val session = OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(1)
            options.setInterOpNumThreads(1)
            try {
                env.createSession(file.path, options)
            } catch (e: Exception) {
                close()
                throw e
            }
        }
        sessions += session
        return session
    }

    private fun run(session: OrtSession, input: FloatArray, shape: LongArray): FloatArray =
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                val buffer = (result.get(0) as OnnxTensor).floatBuffer
                FloatArray(buffer.remaining()).also { buffer.get(it) }
            }
        }

    companion object {
        /** 80 ms at 16 kHz. */
        const val FRAME_SAMPLES = 1280
        const val CONTEXT_SAMPLES = 480
        const val MEL_BANDS = 32
        const val EMBEDDING_ROWS = 76
        const val CLASSIFIER_FRAMES = 16
        const val EMBEDDING_WIDTH = 96
        private const val MEL_FILL = 1f
    }
}
