// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.wakeword

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo

/** Tells an openWakeWord word classifier apart from any other file before it is imported. */
object ClassifierCheck {
    /** Whether [model] loads and takes exactly one input shaped `[?, 16, 96]`: 16 embeddings of 96 values. */
    fun runs(model: ByteArray): Boolean = try {
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(1)
            options.setInterOpNumThreads(1)
            OrtEnvironment.getEnvironment().createSession(model, options).use { session ->
                val inputs = session.inputInfo.values
                val shape = (inputs.singleOrNull()?.info as? TensorInfo)?.shape
                shape != null && shape.size == 3 &&
                    shape[1] == WakeWordEngine.CLASSIFIER_FRAMES.toLong() &&
                    shape[2] == WakeWordEngine.EMBEDDING_WIDTH.toLong()
            }
        }
    } catch (_: OrtException) {
        false
    } catch (_: LinkageError) {
        false
    }
}
