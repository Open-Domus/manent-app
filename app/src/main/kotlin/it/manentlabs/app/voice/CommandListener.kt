// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice

import android.util.Log
import it.manentlabs.app.audio.AudioSink
import it.manentlabs.app.audio.OpusDecoder
import it.manentlabs.app.audio.VoiceDetector
import it.manentlabs.app.audio.WearableAudio
import it.manentlabs.app.voice.capture.CommandCapture
import it.manentlabs.app.voice.wakeword.WakeWordEngine
import java.io.Closeable
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/** What to listen for: each active route's word and its model, plus the shared front-end models. */
data class ListenPlan(val words: List<Armed>, val melModel: File, val embeddingModel: File) {
    data class Armed(val routeId: String, val wordId: String, val classifier: File)
}

/**
 * Listens to the wearable's own stream for the words in a [ListenPlan] and
 * hands each complete command over. Decoding, wake-word scoring and speech
 * detection all run on one worker thread; the models load on the first frame.
 *
 * A frame that fails is dropped and the listener starts over; a model that
 * will not load leaves it deaf, which is only logged.
 */
class CommandListener(
    private val stream: WearableAudio,
    private val plan: ListenPlan,
    private val newVoiceDetector: () -> VoiceDetector?,
    private val events: Events,
) {
    interface Events {
        /** A wake word was heard; the command follows. */
        fun onHeard()

        /** A complete command for the route [routeId], as 16 kHz mono PCM. */
        fun onCommand(routeId: String, pcm: ShortArray)

        /** The wearable's link dropped; this listener is done. */
        fun onLinkLost()
    }

    private val worker: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, THREAD_NAME) }

    // Worker-thread state below.
    private var running = false
    private var decoder: OpusDecoder? = null
    private var capture: CommandCapture? = null
    private var deaf = false
    private val closeables = mutableListOf<Closeable>()

    /** Starts streaming; false when the wearable cannot stream now. */
    fun start(): Boolean {
        val opened = worker.submit(Callable { open() }).get()
        if (!opened) return false
        val started = stream.start(object : AudioSink {
            override fun onFrame(opus: ByteArray, receivedAtMs: Long) {
                offer { if (running) onFrame(opus) }
            }

            override fun onLinkLost() {
                offer {
                    if (!running) return@offer
                    capture?.reset()
                    running = false
                    events.onLinkLost()
                }
            }
        })
        if (!started) worker.execute { running = false }
        return started
    }

    /**
     * Hands [task] to the worker; after [stop] a late frame from the radio is
     * dropped, not thrown. Whatever the task throws is logged, never left to
     * end the process.
     */
    private fun offer(task: () -> Unit) {
        try {
            worker.execute {
                try {
                    task()
                } catch (e: RuntimeException) {
                    Log.e(TAG, "Handling a frame failed", e)
                }
            }
        } catch (e: RejectedExecutionException) {
            Log.d(TAG, "Frame after stop dropped")
        }
    }

    /** Stops listening and frees the models; a command being captured is dropped. */
    fun stop() {
        stream.stop()
        worker.execute {
            running = false
            closeables.forEach { runCatching { it.close() } }
            closeables.clear()
            capture = null
            decoder = null
        }
        worker.shutdown()
    }

    private fun open(): Boolean {
        val d = try {
            OpusDecoder()
        } catch (e: Exception) {
            Log.e(TAG, "No Opus decoder for voice commands", e)
            return false
        }
        decoder = d
        closeables += d
        running = true
        return true
    }

    private fun onFrame(opus: ByteArray) {
        val pcm = decoder?.decode(opus) ?: return
        if (pcm.isEmpty()) return
        val listener = capture ?: load() ?: return
        val heard = try {
            listener.push(pcm)
        } catch (e: Exception) {
            Log.w(TAG, "A frame failed; listening starts over", e)
            listener.reset()
            return
        }
        for (event in heard) {
            when (event) {
                is CommandCapture.Event.Heard -> events.onHeard()
                is CommandCapture.Event.Command -> events.onCommand(plan.words[event.word].routeId, event.pcm)
                is CommandCapture.Event.Dropped -> Log.i(TAG, "Wake word without a command")
            }
        }
    }

    private fun load(): CommandCapture? {
        if (deaf) return null
        return try {
            val engine = WakeWordEngine(plan.melModel, plan.embeddingModel, plan.words.map { it.classifier })
            closeables += engine
            val voice = newVoiceDetector() ?: throw IllegalStateException("No speech detector")
            closeables += voice
            CommandCapture(engine, voice).also { capture = it }
        } catch (e: Exception) {
            Log.e(TAG, "Voice-command models did not load", e)
            deaf = true
            null
        } catch (e: LinkageError) {
            Log.e(TAG, "ONNX Runtime did not load", e)
            deaf = true
            null
        }
    }

    private companion object {
        const val TAG = "Manent:Assist"
        const val THREAD_NAME = "manent-voice-commands"
    }
}
