// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.homeassistant

/**
 * The command audio as Assist's binary messages: each starts with the
 * handler id byte and carries up to 100 ms of 16-bit little-endian PCM; a
 * last message holding only the id byte ends the audio.
 */
object AssistAudio {
    const val SAMPLES_PER_PACKET = 1600

    fun packets(handler: Int, pcm: ShortArray): List<ByteArray> = buildList {
        val id = handler.toByte()
        for (from in pcm.indices step SAMPLES_PER_PACKET) {
            val to = minOf(from + SAMPLES_PER_PACKET, pcm.size)
            val packet = ByteArray(1 + 2 * (to - from))
            packet[0] = id
            for (i in from until to) {
                val sample = pcm[i].toInt()
                val at = 1 + 2 * (i - from)
                packet[at] = sample.toByte()
                packet[at + 1] = (sample shr 8).toByte()
            }
            add(packet)
        }
        add(byteArrayOf(id))
    }
}
