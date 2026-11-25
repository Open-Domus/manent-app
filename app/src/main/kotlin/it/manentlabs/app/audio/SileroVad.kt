// SPDX-License-Identifier: MIT

package it.manentlabs.app.audio

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Silero VAD (v6, the 16 kHz-only export) on ONNX Runtime with one thread.
 *
 * Each 512-sample frame goes in with the last 64 samples of the frame before
 * it as context, and the model's recurrent state is carried from frame to
 * frame. Pure JVM apart from [fromAssets], so unit tests run the real model
 * through onnxruntime-jvm.
 *
 * The constructor throws if the native runtime or the model cannot be loaded.
 */
class SileroVad(model: ByteArray) : VoiceDetector {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = OrtSession.SessionOptions().use { options ->
        options.setIntraOpNumThreads(1)
        options.setInterOpNumThreads(1)
        env.createSession(model, options)
    }
    private val state = FloatArray(STATE_SIZE)
    private val context = FloatArray(CONTEXT)
    private val input = FloatArray(CONTEXT + VoiceDetector.FRAME_SAMPLES)

    override fun score(frame: ShortArray): Float {
        require(frame.size == VoiceDetector.FRAME_SAMPLES) { "A frame is ${VoiceDetector.FRAME_SAMPLES} samples" }
        context.copyInto(input)
        for (i in frame.indices) input[CONTEXT + i] = frame[i] / 32768f
        input.copyInto(context, 0, input.size - CONTEXT)
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, input.size.toLong())).use { audio ->
            OnnxTensor.createTensor(env, FloatBuffer.wrap(state), STATE_SHAPE).use { stateIn ->
                OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(PcmFormat.SAMPLE_RATE.toLong())), longArrayOf()).use { rate ->
                    session.run(mapOf("input" to audio, "state" to stateIn, "sr" to rate)).use { result ->
                        val probability = (result.get(0) as OnnxTensor).floatBuffer.get(0)
                        (result.get(1) as OnnxTensor).floatBuffer.get(state)
                        return probability
                    }
                }
            }
        }
    }

    override fun reset() {
        state.fill(0f)
        context.fill(0f)
    }

    override fun close() = session.close()

    companion object {
        /** The bundled model's asset name (MIT, licence beside it). */
        const val ASSET = "silero_vad.onnx"
        private const val CONTEXT = 64
        private const val STATE_SIZE = 2 * 1 * 128
        private val STATE_SHAPE = longArrayOf(2, 1, 128)

        /** The model bundled with the app, read once per call. */
        fun fromAssets(context: Context): SileroVad =
            SileroVad(context.assets.open(ASSET).use { it.readBytes() })
    }
}
