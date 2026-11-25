// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.transcribe

import it.manentlabs.app.audio.PcmFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A plain RIFF/WAVE file of 16 kHz mono 16-bit PCM, with the usual 44-byte header. */
object Wav {
    private const val HEADER = 44

    fun of(pcm: ShortArray): ByteArray {
        val data = pcm.size * 2
        val buffer = ByteBuffer.allocate(HEADER + data).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(HEADER - 8 + data)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
        buffer.putShort(1).putShort(1)
        buffer.putInt(PcmFormat.SAMPLE_RATE).putInt(PcmFormat.SAMPLE_RATE * 2)
        buffer.putShort(2).putShort(16)
        buffer.put("data".toByteArray(Charsets.US_ASCII)).putInt(data)
        for (sample in pcm) buffer.putShort(sample)
        return buffer.array()
    }
}
