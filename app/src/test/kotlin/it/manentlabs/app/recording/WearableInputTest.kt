// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

import it.manentlabs.app.audio.AudioSink
import it.manentlabs.app.audio.PcmDecoder
import it.manentlabs.app.audio.WearableAudio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WearableInputTest {
    private class FakeStream : WearableAudio {
        var sink: AudioSink? = null
        override val name = "fake"
        override val ready: StateFlow<Boolean> = MutableStateFlow(true)

        override fun start(sink: AudioSink): Boolean {
            this.sink = sink
            return true
        }

        override fun stop() {
            sink = null
        }
    }

    /** Counts packets and yields no samples, so no part is ever opened. */
    private class CountingDecoder : PcmDecoder {
        val decoded = AtomicInteger()

        override fun decode(opus: ByteArray): ShortArray {
            decoded.incrementAndGet()
            return ShortArray(0)
        }

        override fun resetFilter() = Unit

        override fun close() = Unit
    }

    @Test
    fun `every frame from the stream reaches the decoder once`() {
        val stream = FakeStream()
        val decoder = CountingDecoder()
        val input = WearableInput(
            stream,
            partFile = { File("unused-$it") },
            numbers = ChunkNumbers(),
            events = object : InputEvents {
                override fun onPartClosed(part: ClosedPart) = Unit

                override fun onInputLost() = Unit
            },
            detectors = VoiceDetectors(RuntimeEnvironment.getApplication()),
            moments = object : WearableInput.Moments {
                override fun newClipFile() = File("unused-clip")

                override fun onClip(file: File, markedAt: Instant) = Unit
            },
            newDecoder = { decoder },
        )

        assertTrue(input.open())
        val now = System.currentTimeMillis()
        repeat(5) { stream.sink!!.onFrame(byteArrayOf(1, 2, 3), now + it * 20L) }
        input.close()
        input.release()

        assertEquals(5, decoder.decoded.get())
    }
}
