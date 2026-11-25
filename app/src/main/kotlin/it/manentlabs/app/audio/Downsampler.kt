// SPDX-License-Identifier: MIT

package it.manentlabs.app.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Lowers the sample rate of a 16-bit mono stream by a whole [factor] (48 kHz
 * to 16 kHz is 3), filtering first so nothing above the new Nyquist folds
 * back as noise.
 *
 * The filter is a 63-tap Hamming-windowed sinc with its cutoff at 0.9 of the
 * new Nyquist. Its history and the decimation phase carry over between calls,
 * so a stream fed in 20 ms pieces comes out as if it had been fed whole: no
 * clicks at the joins.
 */
class Downsampler(val factor: Int) {
    init {
        require(factor >= 1) { "factor must be at least 1" }
    }

    private val taps = lowPass(TAPS, CUTOFF / (2.0 * factor))
    private val history = DoubleArray(TAPS)
    private var head = 0
    private var phase = 0

    /** Filters and decimates [count] samples of [input] from [offset]; returns the lower-rate samples. */
    fun process(input: ShortArray, offset: Int = 0, count: Int = input.size - offset): ShortArray {
        val out = ShortArray((count + phase) / factor + 1)
        var produced = 0
        for (i in offset until offset + count) {
            history[head] = input[i].toDouble()
            head = (head + 1) % TAPS
            if (phase == 0) out[produced++] = clip(convolve())
            phase = (phase + 1) % factor
        }
        return out.copyOf(produced)
    }

    /** Forgets the filter history, for a stream that starts over. */
    fun reset() {
        history.fill(0.0)
        head = 0
        phase = 0
    }

    private fun convolve(): Double {
        // history[head] is the oldest sample; taps are symmetric, so order does not matter for the result.
        var sum = 0.0
        var index = head
        for (k in 0 until TAPS) {
            sum += taps[k] * history[index]
            index = if (index + 1 == TAPS) 0 else index + 1
        }
        return sum
    }

    private fun clip(value: Double): Short =
        value.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

    companion object {
        const val TAPS = 63
        private const val CUTOFF = 0.9

        /** Windowed-sinc low-pass taps for [cutoff] in cycles per input sample, normalised to unity gain at DC. */
        internal fun lowPass(count: Int, cutoff: Double): DoubleArray {
            val middle = (count - 1) / 2.0
            val taps = DoubleArray(count) { n ->
                val x = n - middle
                val sinc = if (x == 0.0) 2 * cutoff else sin(2 * PI * cutoff * x) / (PI * x)
                val window = 0.54 - 0.46 * cos(2 * PI * n / (count - 1))
                sinc * window
            }
            val gain = taps.sum()
            for (i in taps.indices) taps[i] /= gain
            return taps
        }
    }
}
