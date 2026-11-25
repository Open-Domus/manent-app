// SPDX-License-Identifier: MIT

package it.manentlabs.app.audio

/** The one PCM format the app works in: 16 kHz, mono, 16-bit. */
object PcmFormat {
    const val SAMPLE_RATE = 16_000
    const val SAMPLES_PER_MS = SAMPLE_RATE / 1000

    fun samplesToMs(samples: Long): Long = samples / SAMPLES_PER_MS

    fun msToSamples(ms: Long): Long = ms * SAMPLES_PER_MS

    /** How far a [rate] must be decimated to reach 16 kHz, or null when it is not a whole multiple. */
    fun decimation(rate: Int): Int? = if (rate >= SAMPLE_RATE && rate % SAMPLE_RATE == 0) rate / SAMPLE_RATE else null
}
