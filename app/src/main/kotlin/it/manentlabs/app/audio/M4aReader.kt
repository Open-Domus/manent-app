// SPDX-License-Identifier: MIT

package it.manentlabs.app.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.IOException
import java.nio.ByteOrder

/**
 * Decodes a recorded part back to PCM so its speech can be checked. Only
 * 16 kHz mono 16-bit audio is accepted, the format the app records; anything
 * else, like any decoding failure, throws [IOException].
 */
object M4aReader {
    private const val TIMEOUT_US = 10_000L
    private const val MAX_IDLE_ROUNDS = 200

    fun read(file: File): ShortArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("${file.name} has no audio track")
            val format = extractor.getTrackFormat(track)
            requirePcmFormat(format)
            extractor.selectTrack(track)
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()
            return decode(extractor, codec)
        } catch (e: IOException) {
            throw e
        } catch (e: RuntimeException) {
            throw IOException("Could not decode ${file.name}", e)
        } finally {
            codec?.let {
                try {
                    it.stop()
                } catch (_: IllegalStateException) {
                }
                it.release()
            }
            extractor.release()
        }
    }

    private fun decode(extractor: MediaExtractor, codec: MediaCodec): ShortArray {
        val out = ShortBuilder()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var idle = 0
        while (true) {
            if (!inputDone) {
                val index = codec.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) {
                    val size = extractor.readSampleData(codec.getInputBuffer(index)!!, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> requirePcmFormat(codec.outputFormat)
                index >= 0 -> {
                    idle = 0
                    val buffer = codec.getOutputBuffer(index)!!
                    buffer.position(info.offset).limit(info.offset + info.size)
                    out.append(buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer())
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return out.build()
                }
                inputDone && ++idle > MAX_IDLE_ROUNDS -> return out.build()
            }
        }
    }

    private fun requirePcmFormat(format: MediaFormat) {
        val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            format.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else {
            AudioFormat.ENCODING_PCM_16BIT
        }
        if (rate != PcmFormat.SAMPLE_RATE || channels != 1 || encoding != AudioFormat.ENCODING_PCM_16BIT) {
            throw IOException("Not 16 kHz mono 16-bit audio: $rate Hz, $channels channels, encoding $encoding")
        }
    }

    private class ShortBuilder {
        private var data = ShortArray(PcmFormat.SAMPLE_RATE * 8)
        private var size = 0

        fun append(source: java.nio.ShortBuffer) {
            val n = source.remaining()
            if (size + n > data.size) data = data.copyOf(maxOf(data.size * 2, size + n))
            source.get(data, size, n)
            size += n
        }

        fun build(): ShortArray = data.copyOf(size)
    }
}
