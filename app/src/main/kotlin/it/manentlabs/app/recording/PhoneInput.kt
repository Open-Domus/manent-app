// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

import android.content.Context
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import it.manentlabs.app.audio.AacWriter
import it.manentlabs.app.audio.PcmFormat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.FutureTask

/**
 * Records from the phone's microphone with the platform recorder, which
 * writes AAC directly. Parts rotate on size: as one nears 240 kB the recorder
 * is handed the next file and switches without stopping, so nothing is lost.
 * Everything runs on one thread of its own, where the recorder's events land.
 */
class PhoneInput(
    private val context: Context,
    private val partFile: (Int) -> File,
    private val numbers: ChunkNumbers,
    private val events: InputEvents,
) : InputSource {
    private class Part(val seq: Int, val file: File, var startedAtMs: Long = 0, var interrupted: Boolean = false)

    private val thread = HandlerThread("Manent:Mic").apply { start() }
    private val handler = Handler(thread.looper)
    private var recorder: MediaRecorder? = null
    private var current: Part? = null
    private var next: Part? = null

    override fun open(): Boolean = onThread { begin() }

    override fun close() = onThread { end() }

    override fun release() {
        thread.quitSafely()
    }

    private fun begin(): Boolean {
        val part = newPart()
        val started = try {
            recorder = configure(part.file).also {
                it.prepare()
                it.start()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Microphone did not open", e)
            recorder?.release()
            recorder = null
            false
        }
        if (!started) {
            part.file.delete()
            numbers.giveBack(part.seq)
            return false
        }
        part.startedAtMs = System.currentTimeMillis()
        current = part
        watchSilencing()
        return true
    }

    private fun newPart(): Part {
        val seq = numbers.reserve()
        return Part(seq, partFile(seq))
    }

    private fun configure(file: File): MediaRecorder {
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(source())
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioSamplingRate(PcmFormat.SAMPLE_RATE)
        r.setAudioChannels(1)
        r.setAudioEncodingBitRate(AacWriter.BIT_RATE)
        r.setMaxFileSize(RecordingLimits.PHONE_CHUNK_BYTES)
        r.setOutputFile(file.path)
        r.setOnInfoListener { _, what, _ -> onInfo(what) }
        return r
    }

    /** Unprocessed where the device supports it, else the voice-recognition source: no gain control, no noise suppression. */
    private fun source(): Int {
        val audio = context.getSystemService(AudioManager::class.java)
        val unprocessed = audio?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        return if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
    }

    private fun onInfo(what: Int) {
        when (what) {
            MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_APPROACHING -> queueNext()
            MediaRecorder.MEDIA_RECORDER_INFO_NEXT_OUTPUT_FILE_STARTED -> switchToNext()
            MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED -> restart()
        }
    }

    private fun queueNext() {
        val r = recorder ?: return
        if (next != null) return
        val part = newPart()
        try {
            r.setNextOutputFile(part.file)
            next = part
        } catch (e: IOException) {
            Log.w(TAG, "Could not queue the next part", e)
            numbers.giveBack(part.seq)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Could not queue the next part", e)
            numbers.giveBack(part.seq)
        }
    }

    private fun switchToNext() {
        val finished = current ?: return
        val following = next ?: return
        val now = System.currentTimeMillis()
        report(finished, now - finished.startedAtMs)
        following.startedAtMs = now
        current = following
        next = null
    }

    /** The next file was not queued in time: the recorder stopped at the limit. Start over with a few ms lost. */
    private fun restart() {
        val finished = current ?: return
        val now = System.currentTimeMillis()
        recorder?.release()
        recorder = null
        current = null
        dropQueued()
        if (begin()) {
            report(finished, now - finished.startedAtMs)
        } else {
            finished.interrupted = true
            report(finished, now - finished.startedAtMs)
            events.onInputLost()
        }
    }

    private fun end() {
        val r = recorder ?: return
        val part = current
        val stopped = try {
            r.stop()
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "The recorder stopped without audio", e)
            false
        }
        r.release()
        recorder = null
        current = null
        dropQueued()
        if (part != null) {
            if (!stopped) FileOutputStream(part.file).close()
            report(part, if (stopped) System.currentTimeMillis() - part.startedAtMs else 0)
        }
    }

    private fun dropQueued() {
        next?.let {
            it.file.delete()
            numbers.giveBack(it.seq)
        }
        next = null
    }

    private fun report(part: Part, durationMs: Long) {
        events.onPartClosed(ClosedPart(part.seq, part.file, part.startedAtMs, durationMs, part.interrupted))
    }

    /** Android 10+: flags the part when another app takes the microphone and ours records silence. */
    private fun watchSilencing() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val r = recorder ?: return
        r.registerAudioRecordingCallback({ handler.post(it) }, object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
                val mine = recorder?.takeIf { it === r } ?: return
                if (mine.activeRecordingConfiguration?.isClientSilenced == true) current?.interrupted = true
            }
        })
    }

    private fun <T> onThread(block: () -> T): T {
        if (Looper.myLooper() == thread.looper) return block()
        val task = FutureTask(block)
        handler.post(task)
        return task.get()
    }

    private companion object {
        const val TAG = "Manent:Mic"
    }
}
