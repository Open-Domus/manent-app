// SPDX-License-Identifier: MIT

package it.manentlabs.app.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class DownsamplerTest {
    private fun tone(hz: Double, samples: Int, rate: Int = 48_000, amplitude: Double = 10_000.0) =
        ShortArray(samples) { (amplitude * sin(2 * PI * hz * it / rate)).roundToInt().toShort() }

    private fun rms(pcm: ShortArray, skip: Int = Downsampler.TAPS): Double {
        val body = pcm.drop(skip)
        return sqrt(body.sumOf { it.toDouble() * it } / body.size)
    }

    @Test
    fun `taps have unity gain and are symmetric`() {
        val taps = Downsampler.lowPass(63, 0.9 / 6)
        assertEquals(1.0, taps.sum(), 1e-9)
        for (i in taps.indices) assertEquals(taps[i], taps[taps.size - 1 - i], 1e-12)
    }

    @Test
    fun `decimates by the factor`() {
        val out = Downsampler(3).process(ShortArray(960))
        assertEquals(320, out.size)
    }

    @Test
    fun `a constant level passes unchanged once the filter has filled`() {
        val out = Downsampler(3).process(ShortArray(4_800) { 1_000 })
        assertTrue(out.drop(30).all { abs(it - 1_000) <= 1 })
    }

    @Test
    fun `feeding in frames gives the same result as feeding whole`() {
        val input = tone(440.0, 48_000)
        val whole = Downsampler(3).process(input)
        val framed = Downsampler(3)
        val pieces = (0 until input.size step 961).flatMap { start ->
            framed.process(input, start, minOf(961, input.size - start)).toList()
        }
        assertArrayEquals(whole, pieces.toShortArray())
    }

    @Test
    fun `speech band passes and what would alias is removed`() {
        val passed = Downsampler(3).process(tone(1_000.0, 48_000))
        val blocked = Downsampler(3).process(tone(12_000.0, 48_000))
        assertEquals(10_000 / sqrt(2.0), rms(passed), 300.0)
        assertTrue("12 kHz should be gone, rms ${rms(blocked)}", rms(blocked) < 100)
    }

    @Test
    fun `reset forgets the history`() {
        val d = Downsampler(3)
        d.process(ShortArray(300) { 20_000 })
        d.reset()
        assertTrue(d.process(ShortArray(30)).all { it.toInt() == 0 })
    }
}
