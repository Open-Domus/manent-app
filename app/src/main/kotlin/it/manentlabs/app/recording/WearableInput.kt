// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

import android.util.Log
import it.manentlabs.app.audio.AacWriter
import it.manentlabs.app.audio.AudioSink
import it.manentlabs.app.audio.FrameSplitter
import it.manentlabs.app.audio.OpusDecoder
import it.manentlabs.app.audio.PcmDecoder
import it.manentlabs.app.audio.PcmFormat
import it.manentlabs.app.audio.PcmRing
import it.manentlabs.app.audio.VoiceDetector
import it.manentlabs.app.audio.WearableAudio
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Records from the wearable's own stream over the shared audio hub: decodes
 * each Opus frame to 16 kHz PCM and encodes it to AAC, a sentence at a time.
 *
 * Frames are handed to a worker thread at once, so the hub is never held up.
 * The worker lays frames on the wall clock (filling late ones with silence,
 * filing long gaps on their own), cuts parts at sentence ends, keeps the last
 * 30 s for marked moments, and writes the clip of a moment being marked.
 */
class WearableInput(
    private val stream: WearableAudio,
    private val partFile: (Int) -> File,
    private val numbers: ChunkNumbers,
    private val events: InputEvents,
    private val detectors: VoiceDetectors,
    private val moments: Moments,
    private val newDecoder: () -> PcmDecoder = ::OpusDecoder,
) : InputSource {
    /** Where marked-moment clips come from and go. */
    interface Moments {
        fun newClipFile(): File

        fun onClip(file: File, markedAt: Instant)
    }

    private class Part(val seq: Int, val file: File, val startedAtMs: Long, val writer: AacWriter)

    private val worker: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "Manent:WearRec") }

    // Worker-thread state below.
    private var run = 0
    private var streaming = false
    private var decoder: PcmDecoder? = null
    private var detector: VoiceDetector? = null
    private var detectorBroken = false
    private val timeline = WearableTimeline()
    private val splitter = SentenceSplitter()
    private val frames = FrameSplitter()
    private val preRoll = PcmRing(PcmFormat.msToSamples(RecordingLimits.PRE_ROLL_MS).toInt())
    private var part: Part? = null
    private var nextPartStartMs = 0L
    private var clip: Clip? = null

    override fun open(): Boolean {
        if (!stream.ready.value) return false
        val myRun = onWorker { beginRun() }
        if (myRun == null) return false
        val started = stream.start(object : AudioSink {
            override fun onFrame(opus: ByteArray, receivedAtMs: Long) {
                offer("A frame") { if (run == myRun) take(opus, receivedAtMs) }
            }

            override fun onLinkLost() {
                offer("Handling the lost link") {
                    if (run != myRun) return@offer
                    endRun(interrupted = true)
                    events.onInputLost()
                }
            }
        })
        if (!started) onWorker { endRun(interrupted = false) }
        return started
    }

    override fun close() {
        stream.stop()
        onWorker { endRun(interrupted = false) }
    }

    override fun release() {
        stream.stop()
        offer("Freeing the decoders") {
            decoder?.close()
            decoder = null
            detector?.close()
            detector = null
        }
        worker.shutdown()
    }

    /**
     * Starts a clip with the last 30 s heard, continuing until the next pause.
     * False when the stream is not running, a clip is already open, or the
     * file cannot be written. Suspends until the worker has answered, so the
     * answer is always the real outcome.
     */
    suspend fun markMoment(): Boolean {
        val answer = CompletableDeferred<Boolean>()
        try {
            worker.execute {
                answer.complete(
                    try {
                        startClip()
                    } catch (e: RuntimeException) {
                        Log.w(TAG, "Could not mark a moment", e)
                        false
                    },
                )
            }
        } catch (_: RejectedExecutionException) {
            return false
        }
        return answer.await()
    }

    /**
     * Runs [task] on the worker. An exception it throws is logged rather than
     * left to end the process; a task arriving after [release] is dropped.
     */
    private fun offer(what: String, task: () -> Unit) {
        try {
            worker.execute {
                try {
                    task()
                } catch (e: RuntimeException) {
                    Log.e(TAG, "$what failed", e)
                }
            }
        } catch (_: RejectedExecutionException) {
            Log.d(TAG, "$what arrived after release; dropped")
        }
    }

    private fun beginRun(): Int? {
        val d = decoder ?: try {
            newDecoder().also { decoder = it }
        } catch (e: Exception) {
            Log.e(TAG, "No Opus decoder", e)
            return null
        }
        d.resetFilter()
        run++
        streaming = true
        timeline.reset()
        resetDetection()
        preRoll.clear()
        return run
    }

    private fun endRun(interrupted: Boolean) {
        if (!streaming) return
        run++
        streaming = false
        closePart(interrupted)
        endClip()
    }

    private fun take(opus: ByteArray, receivedAtMs: Long) {
        when (val placement = timeline.place(receivedAtMs)) {
            is WearableTimeline.Placement.Open -> nextPartStartMs = placement.startMs
            is WearableTimeline.Placement.Gap -> {
                closePart(interrupted = false)
                endClip()
                if (placement.gapMs > 0) {
                    events.onPartClosed(ClosedPart(numbers.reserve(), null, placement.gapStartMs, placement.gapMs, interrupted = false))
                }
                nextPartStartMs = placement.resumeAtMs
                decoder?.resetFilter()
                resetDetection()
            }
            is WearableTimeline.Placement.Follow -> if (placement.silenceMs > 0) {
                write(ShortArray(PcmFormat.msToSamples(placement.silenceMs).toInt()))
            }
        }
        val pcm = decoder?.decode(opus) ?: return
        if (pcm.isNotEmpty()) write(pcm)
    }

    private fun write(pcm: ShortArray) {
        val open = part ?: openPart() ?: return
        try {
            open.writer.write(pcm)
        } catch (e: IOException) {
            Log.w(TAG, "Encoder failed mid-part", e)
            closePart(interrupted = true)
            return
        }
        frames.push(pcm) { splitter.onFrame(score(it) ?: return@push) }
        preRoll.push(pcm)
        clip?.let { if (it.write(pcm)) endClip() }
        if (splitter.shouldCut(PcmFormat.samplesToMs(open.writer.samplesWritten))) closePart(interrupted = false)
    }

    private fun openPart(): Part? {
        val seq = numbers.reserve()
        val file = partFile(seq)
        return try {
            Part(seq, file, nextPartStartMs, AacWriter(file)).also {
                part = it
                splitter.onPartOpened()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Could not open a part", e)
            numbers.giveBack(seq)
            null
        }
    }

    private fun closePart(interrupted: Boolean) {
        val p = part ?: return
        part = null
        val failed = try {
            p.writer.finish()
            false
        } catch (e: IOException) {
            Log.w(TAG, "Part did not finish cleanly", e)
            true
        }
        val durationMs = PcmFormat.samplesToMs(p.writer.samplesWritten)
        nextPartStartMs = p.startedAtMs + durationMs
        events.onPartClosed(ClosedPart(p.seq, p.file, p.startedAtMs, durationMs, interrupted || failed))
    }

    /** The live detector's score for one frame; null once it has failed, which leaves cuts to length alone. */
    private fun score(frame: ShortArray): Float? {
        if (detectorBroken) return null
        val d = detector ?: detectors.create()?.also { detector = it }
        if (d == null) {
            breakDetector()
            return null
        }
        return try {
            d.score(frame)
        } catch (e: Exception) {
            Log.w(TAG, "Speech detector failed; cutting by length", e)
            breakDetector()
            null
        }
    }

    private fun breakDetector() {
        detectorBroken = true
        splitter.onDetectorFailed()
    }

    private fun resetDetection() {
        frames.clear()
        detector?.reset()
        splitter.onPartOpened()
    }

    private fun startClip(): Boolean {
        if (!streaming || clip != null) return false
        val file = moments.newClipFile()
        val writer = try {
            AacWriter(file)
        } catch (e: IOException) {
            Log.w(TAG, "Could not open a clip", e)
            file.delete()
            return false
        }
        val held = preRoll.snapshot()
        return try {
            writer.write(held)
            clip = Clip(file, writer, Instant.now(), detectors.create())
            true
        } catch (_: IOException) {
            writer.close()
            file.delete()
            false
        }
    }

    private fun endClip() {
        val c = clip ?: return
        clip = null
        if (c.finish()) moments.onClip(c.file, c.markedAt) else c.file.delete()
    }

    /** A marked moment being written: the pre-roll, then what follows until [ClipEnding] says stop. */
    private class Clip(val file: File, private val writer: AacWriter, val markedAt: Instant, private val detector: VoiceDetector?) {
        private val ending = ClipEnding(hasDetector = detector != null)
        private val frames = FrameSplitter()
        private var failed = false

        /** Adds audio heard after the mark; true when the clip should end. */
        fun write(pcm: ShortArray): Boolean {
            try {
                writer.write(pcm)
            } catch (_: IOException) {
                failed = true
                return true
            }
            ending.onAudio(PcmFormat.samplesToMs(pcm.size.toLong()))
            if (detector != null) {
                frames.push(pcm) { frame ->
                    ending.onFrame(
                        try {
                            detector.score(frame)
                        } catch (_: Exception) {
                            null
                        },
                    )
                }
            }
            return ending.shouldEnd()
        }

        /** Closes the clip; true when it holds audio worth keeping. */
        fun finish(): Boolean {
            detector?.close()
            return try {
                writer.finish() && !failed
            } catch (_: IOException) {
                false
            }
        }
    }

    private fun <T> onWorker(block: () -> T): T = worker.submit(Callable(block)).get()

    private companion object {
        const val TAG = "Manent:WearRec"
    }
}
