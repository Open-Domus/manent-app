// SPDX-License-Identifier: MIT

package it.manentlabs.app.audio

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Runs the bundled model on the JVM through onnxruntime-jvm. */
class SileroVadTest {
    private fun frames(count: Int, sample: (Int) -> Double): List<ShortArray> =
        (0 until count).map { f -> ShortArray(VoiceDetector.FRAME_SAMPLES) { sample(f * VoiceDetector.FRAME_SAMPLES + it).toInt().toShort() } }

    @Test
    fun `silence scores low`() {
        vad.reset()
        val scores = frames(20) { 0.0 }.map(vad::score)
        assertTrue(scores.all { it in 0f..0.1f })
    }

    @Test
    fun `a steady tone or noise is not speech`() {
        vad.reset()
        val random = Random(1)
        val tone = frames(30) { 3_000 * sin(2 * PI * 440 * it / 16_000.0) + random.nextInt(-200, 200) }.map(vad::score)
        assertTrue(tone.all { it in 0f..1f })
        assertTrue("mean ${tone.average()}", tone.average() < 0.5)
    }

    @Test
    fun `reset makes scoring repeatable`() {
        val input = frames(10) { 2_000 * sin(2 * PI * 300 * it / 16_000.0) }
        vad.reset()
        val first = input.map(vad::score)
        vad.reset()
        val second = input.map(vad::score)
        for (i in first.indices) assertEquals(first[i], second[i], 1e-6f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `frames must be 512 samples`() {
        vad.score(ShortArray(100))
    }

    companion object {
        private val vad = SileroVad(File("src/main/assets/${SileroVad.ASSET}").readBytes())

        @JvmStatic
        @AfterClass
        fun close() = vad.close()
    }
}
