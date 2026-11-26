// SPDX-License-Identifier: MIT

package it.manentlabs.app.audio

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Turns a wearable's packets into 16 kHz mono PCM; [OpusDecoder] on a device. */
interface PcmDecoder : Closeable {
    /** One packet's samples, possibly none; null when the packet was dropped. */
    fun decode(opus: ByteArray): ShortArray?

    /** Forgets the filter history, for audio that does not follow on from what came before. */
    fun resetFilter()
}

/**
 * Turns a wearable's Opus packets into 16 kHz mono 16-bit PCM.
 *
 * The platform decoder runs at 48 kHz; [Downsampler] brings it down. If a
 * device's decoder reports another whole multiple of 16 kHz, the decimation
 * follows it; any other rate drops every packet. Use from one thread.
 *
 * Nothing thrown while decoding leaves [decode]: it runs on the threads that
 * carry the wearable's audio, where an escaped exception ends the process.
 */
class OpusDecoder : PcmDecoder {
    private var codec: MediaCodec? = newCodec()
    private var downsampler = Downsampler(DECODER_RATE / PcmFormat.SAMPLE_RATE)
    private var presentationUs = 0L
    private val info = MediaCodec.BufferInfo()

    /**
     * Decodes one packet. Returns its 16 kHz samples (possibly none while the
     * decoder warms up), or null when the packet could not be decoded; that
     * packet is then dropped and logged. A decoder that failed for good is
     * replaced; if no new one can be made, every later packet is dropped.
     */
    override fun decode(opus: ByteArray): ShortArray? {
        val c = codec ?: return null
        return try {
            queue(c, opus)
            drain(c)
        } catch (e: MediaCodec.CodecException) {
            Log.w(TAG, "Dropped an undecodable frame: ${e.diagnosticInfo}")
            if (!e.isRecoverable && !e.isTransient) replace(c)
            null
        } catch (e: UnsupportedRate) {
            Log.e(TAG, "Dropping all audio: ${e.message}")
            releaseQuietly(c)
            codec = null
            null
        } catch (e: RuntimeException) {
            Log.w(TAG, "Dropped an undecodable frame", e)
            null
        }
    }

    override fun resetFilter() = downsampler.reset()

    override fun close() {
        codec?.let(::releaseQuietly)
        codec = null
    }

    /** Swaps a codec in its Error state, where it accepts nothing but release, for a fresh one. */
    private fun replace(failed: MediaCodec) {
        releaseQuietly(failed)
        codec = try {
            newCodec()
        } catch (e: Exception) {
            Log.e(TAG, "The Opus decoder failed and could not be restarted; dropping audio", e)
            null
        }
        presentationUs = 0
        downsampler.reset()
    }

    private fun queue(codec: MediaCodec, opus: ByteArray) {
        val index = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
        check(index >= 0) { "No decoder input buffer free" }
        val buffer = codec.getInputBuffer(index)!!
        buffer.clear()
        buffer.put(opus)
        codec.queueInputBuffer(index, 0, opus.size, presentationUs, 0)
        presentationUs += FRAME_US
    }

    private fun drain(codec: MediaCodec): ShortArray {
        var out = ShortArray(0)
        var timeout = OUTPUT_TIMEOUT_US
        while (true) {
            val index = codec.dequeueOutputBuffer(info, timeout)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> adopt(codec.outputFormat)
                index >= 0 -> {
                    val buffer = codec.getOutputBuffer(index)!!
                    buffer.position(info.offset).limit(info.offset + info.size)
                    val pcm = ShortArray(info.size / 2)
                    buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm)
                    codec.releaseOutputBuffer(index, false)
                    out += downsampler.process(pcm)
                    timeout = 0
                }
                else -> return out
            }
        }
    }

    private fun adopt(format: MediaFormat) {
        val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val factor = PcmFormat.decimation(rate)
            ?: throw UnsupportedRate("the Opus decoder runs at $rate Hz, not a multiple of 16 kHz")
        if (factor != downsampler.factor) downsampler = Downsampler(factor)
    }

    private class UnsupportedRate(message: String) : IllegalStateException(message)

    private fun releaseQuietly(c: MediaCodec) {
        try {
            c.stop()
        } catch (_: IllegalStateException) {
        }
        try {
            c.release()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Decoder release failed", e)
        }
    }

    private companion object {
        const val TAG = "Manent:Opus"
        const val DECODER_RATE = 48_000
        const val FRAME_US = 20_000L
        const val SEEK_PRE_ROLL_NS = 80_000_000L
        const val INPUT_TIMEOUT_US = 10_000L
        const val OUTPUT_TIMEOUT_US = 10_000L

        /** The identification header: mono, no pre-skip, 16 kHz original input, no gain, mapping family 0. */
        /** A started Opus decoder; throws when the device cannot make one. */
        fun newCodec(): MediaCodec {
            val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, DECODER_RATE, 1).apply {
                setByteBuffer("csd-0", opusHead())
                setByteBuffer("csd-1", longLe(0))
                setByteBuffer("csd-2", longLe(SEEK_PRE_ROLL_NS))
            }
            try {
                codec.configure(format, null, null, 0)
                codec.start()
            } catch (e: RuntimeException) {
                codec.release()
                throw e
            }
            return codec
        }

        fun opusHead(): ByteBuffer = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("OpusHead".toByteArray(Charsets.US_ASCII))
            put(1)
            put(1)
            putShort(0)
            putInt(PcmFormat.SAMPLE_RATE)
            putShort(0)
            put(0)
            flip()
        }

        fun longLe(value: Long): ByteBuffer =
            ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).apply { flip() }
    }
}
