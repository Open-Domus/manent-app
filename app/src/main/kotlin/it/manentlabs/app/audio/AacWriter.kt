// SPDX-License-Identifier: MIT

package it.manentlabs.app.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Encodes 16 kHz mono PCM to AAC-LC at 32 kbit/s in an MPEG-4 file (`.m4a`,
 * `audio/mp4`), the one format every recorded part and marked moment uses.
 *
 * Call [write] as audio arrives and [finish] once. A file that ends up with
 * no encoded audio is left at 0 bytes. Any failure throws [IOException]; the
 * writer is then unusable but [close] still frees it. Use from one thread.
 */
class AacWriter(val file: File) : Closeable {
    private val codec: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private val muxer: MediaMuxer
    private val info = MediaCodec.BufferInfo()
    private var track = -1
    private var muxing = false
    private var released = false
    private var queuedSamples = 0L

    /** Samples handed to [write] so far. */
    var samplesWritten = 0L
        private set

    /** Whether any encoded audio reached the file. */
    var hasAudio = false
        private set

    init {
        try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, PcmFormat.SAMPLE_RATE, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_BYTES)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (e: Exception) {
            codec.release()
            throw IOException("Could not open the AAC encoder for ${file.name}", e)
        }
    }

    /** Encodes [count] samples of [pcm] from [offset]. */
    fun write(pcm: ShortArray, offset: Int = 0, count: Int = pcm.size - offset) = guarded {
        var i = offset
        val end = offset + count
        while (i < end) {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index < 0) {
                drain(endOfStream = false)
                continue
            }
            val buffer = codec.getInputBuffer(index)!!
            buffer.clear()
            val n = minOf(end - i, buffer.remaining() / 2)
            for (k in 0 until n) {
                val s = pcm[i + k].toInt()
                buffer.put(s.toByte())
                buffer.put((s shr 8).toByte())
            }
            codec.queueInputBuffer(index, 0, n * 2, presentationUs(), 0)
            queuedSamples += n
            i += n
            drain(endOfStream = false)
        }
        samplesWritten += count
    }

    /** Flushes the encoder and closes the file. Returns true when it holds audio. */
    fun finish(): Boolean {
        try {
            guarded {
                val index = codec.dequeueInputBuffer(FINISH_TIMEOUT_US)
                if (index >= 0) {
                    codec.queueInputBuffer(index, 0, 0, presentationUs(), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    drain(endOfStream = true)
                }
            }
        } finally {
            close()
            if (!hasAudio) FileOutputStream(file).close()
        }
        return hasAudio
    }

    override fun close() {
        if (released) return
        released = true
        try {
            if (muxing) muxer.stop()
        } catch (_: IllegalStateException) {
        }
        muxer.release()
        try {
            codec.stop()
        } catch (_: IllegalStateException) {
        }
        codec.release()
    }

    private fun presentationUs() = queuedSamples * 1_000_000L / PcmFormat.SAMPLE_RATE

    private fun drain(endOfStream: Boolean) {
        var idleRounds = 0
        while (true) {
            val index = codec.dequeueOutputBuffer(info, if (endOfStream) TIMEOUT_US else 0)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxing = true
                }
                index >= 0 -> {
                    val buffer = codec.getOutputBuffer(index)!!
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (!isConfig && info.size > 0 && muxing) {
                        buffer.position(info.offset).limit(info.offset + info.size)
                        muxer.writeSampleData(track, buffer, info)
                        hasAudio = true
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
                !endOfStream -> return
                ++idleRounds > MAX_IDLE_ROUNDS -> return
            }
        }
    }

    private inline fun guarded(block: () -> Unit) {
        check(!released) { "The writer is closed" }
        try {
            block()
        } catch (e: IllegalStateException) {
            throw IOException("Encoding ${file.name} failed", e)
        } catch (e: IllegalArgumentException) {
            throw IOException("Encoding ${file.name} failed", e)
        }
    }

    companion object {
        const val MIME_TYPE = "audio/mp4"
        const val EXTENSION = "m4a"
        const val BIT_RATE = 32_000
        private const val MAX_INPUT_BYTES = 16 * 1024
        private const val TIMEOUT_US = 10_000L
        private const val FINISH_TIMEOUT_US = 100_000L
        private const val MAX_IDLE_ROUNDS = 50
    }
}
