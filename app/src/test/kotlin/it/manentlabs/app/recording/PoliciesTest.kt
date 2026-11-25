// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

import it.manentlabs.app.gateway.GatewayError
import it.manentlabs.app.gateway.RecordingAvailability
import it.manentlabs.app.recording.StartPolicy.Decision
import it.manentlabs.app.recording.StartPolicy.Phase
import it.manentlabs.app.wearable.RecordingInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PoliciesTest {
    @Test
    fun `sentence splitter cuts at a pause once long enough`() {
        val s = SentenceSplitter()
        s.onFrame(0.9f)
        repeat(22) { s.onFrame(0.1f) }
        assertTrue("704 ms of quiet after speech", s.shouldCut(10_000))
        assertFalse("too short", s.shouldCut(9_999))
    }

    @Test
    fun `sentence splitter waits for a long enough pause and for speech`() {
        val s = SentenceSplitter()
        s.onFrame(0.9f)
        repeat(21) { s.onFrame(0.1f) }
        assertFalse(s.shouldCut(20_000))
        s.onFrame(0.4f)
        repeat(30) { s.onFrame(0.1f) }
        assertTrue(s.shouldCut(20_000))

        s.onPartOpened()
        repeat(100) { s.onFrame(0.1f) }
        assertFalse("quiet alone runs to full length", s.shouldCut(59_999))
        assertTrue(s.shouldCut(60_000))
    }

    @Test
    fun `sentence splitter falls back to length after a detector failure`() {
        val s = SentenceSplitter()
        s.onFrame(0.9f)
        repeat(30) { s.onFrame(0.1f) }
        s.onDetectorFailed()
        assertFalse(s.shouldCut(30_000))
        assertTrue(s.shouldCut(60_000))
    }

    @Test
    fun `timeline opens, follows, fills late frames and files gaps`() {
        val t = WearableTimeline()
        assertEquals(WearableTimeline.Placement.Open(980), t.place(1_000))
        assertEquals(WearableTimeline.Placement.Follow(0), t.place(1_020))
        assertEquals(WearableTimeline.Placement.Follow(0), t.place(1_300))
        assertEquals(WearableTimeline.Placement.Follow(480), t.place(1_540))
        assertEquals(WearableTimeline.Placement.Gap(gapStartMs = 1_540, gapMs = 2_480, resumeAtMs = 4_020), t.place(4_040))
        assertEquals(WearableTimeline.Placement.Follow(0), t.place(4_060))
        t.reset()
        assertEquals(WearableTimeline.Placement.Open(9_980), t.place(10_000))
    }

    @Test
    fun `episode closes after 90 s without speech`() {
        val clock = EpisodeClock(startMs = 0)
        assertFalse(clock.expired(89_999))
        assertTrue(clock.expired(90_000))
        clock.onSpeechPart(endMs = 50_000)
        assertFalse(clock.expired(139_999))
        assertTrue(clock.expired(140_000))
        clock.onSpeechPart(endMs = 10_000)
        assertTrue("an earlier end does not wind it back", clock.expired(140_000))
    }

    @Test
    fun `clip ends at a pause after 2 s`() {
        val c = ClipEnding(hasDetector = true)
        repeat(38) { c.onFrame(0.1f) }
        c.onAudio(1_216)
        assertFalse("not yet 2 s since the mark", c.shouldEnd())
        c.onAudio(800)
        assertTrue(c.shouldEnd())
    }

    @Test
    fun `clip speech or detector errors keep it going until 60 s`() {
        val c = ClipEnding(hasDetector = true)
        repeat(30) { c.onFrame(0.1f) }
        c.onFrame(null)
        repeat(30) { c.onFrame(0.1f) }
        c.onAudio(10_000)
        assertFalse(c.shouldEnd())
        c.onAudio(50_000)
        assertTrue(c.shouldEnd())

        val blind = ClipEnding(hasDetector = false)
        repeat(100) { blind.onFrame(0.1f) }
        blind.onAudio(59_000)
        assertFalse(blind.shouldEnd())
    }

    @Test
    fun `numbers stay dense and only the latest is given back`() {
        val n = ChunkNumbers()
        assertEquals(0, n.reserve())
        assertEquals(1, n.reserve())
        assertFalse(n.giveBack(0))
        assertTrue(n.giveBack(1))
        assertEquals(1, n.count)
        assertEquals(1, n.reserve())
    }

    private val user = StartRequest(RecordingInput.PHONE, "Lunch", ambient = false)
    private val episode = StartRequest(RecordingInput.WEARABLE, null, ambient = true)

    @Test
    fun `start decisions`() {
        assertEquals(Decision.BEGIN, StartPolicy.decide(Phase.IDLE, false, null, episode))
        assertEquals(Decision.REPLACE_EPISODE, StartPolicy.decide(Phase.RUNNING, true, null, user))
        assertEquals(Decision.REPLACE_EPISODE, StartPolicy.decide(Phase.STARTING, true, null, user))
        assertEquals(Decision.TAKE_NEXT_EPISODE, StartPolicy.decide(Phase.CLOSING, true, true, user))
        assertEquals(Decision.IGNORE, StartPolicy.decide(Phase.CLOSING, true, null, user))
        assertEquals(Decision.IGNORE, StartPolicy.decide(Phase.RUNNING, false, null, user))
        assertEquals(Decision.IGNORE, StartPolicy.decide(Phase.RUNNING, true, null, episode))
    }

    @Test
    fun `input choice prefers the wearable while it is linked`() {
        assertEquals(RecordingInput.WEARABLE, InputChoice.effective(null, wearableReady = true))
        assertEquals(RecordingInput.PHONE, InputChoice.effective(null, wearableReady = false))
        assertEquals(RecordingInput.PHONE, InputChoice.effective(RecordingInput.PHONE, wearableReady = true))
    }

    @Test
    fun `recording needs a paired gateway that takes recordings`() {
        assertEquals(RecordingGate.NOT_PAIRED, RecordingGate.of(false, RecordingAvailability.AVAILABLE))
        assertEquals(RecordingGate.OPEN, RecordingGate.of(true, RecordingAvailability.AVAILABLE))
        assertEquals(RecordingGate.TRANSCRIPTION_OFF, RecordingGate.of(true, RecordingAvailability.TRANSCRIPTION_OFF))
        assertEquals(RecordingGate.UNAVAILABLE, RecordingGate.of(true, RecordingAvailability.UNAVAILABLE))
    }

    @Test
    fun `drain failures`() {
        assertEquals(DrainPolicy.FailureAction.Reopen, DrainPolicy.onFailure(GatewayError.NotFound(null)))
        assertEquals(DrainPolicy.FailureAction.Refuse("too big"), DrainPolicy.onFailure(GatewayError.Server(413, "too big", null)))
        assertEquals(
            DrainPolicy.FailureAction.Refuse("The gateway refused the recording (409)."),
            DrainPolicy.onFailure(GatewayError.Server(409, " ", null)),
        )
        assertEquals(DrainPolicy.FailureAction.Retry, DrainPolicy.onFailure(GatewayError.Server(400, "off", GatewayError.DICTATION_DISABLED)))
        assertEquals(DrainPolicy.FailureAction.Retry, DrainPolicy.onFailure(GatewayError.Forbidden("off", GatewayError.DICTATION_DISABLED)))
        assertEquals(DrainPolicy.FailureAction.Retry, DrainPolicy.onFailure(GatewayError.Server(500, "boom", null)))
        assertEquals(DrainPolicy.FailureAction.Retry, DrainPolicy.onFailure(GatewayError.Network(IOException("down"))))
        assertEquals(DrainPolicy.FailureAction.Retry, DrainPolicy.onFailure(GatewayError.Unauthorized()))
    }

    private fun session(ambient: Boolean = false, hasAudio: Boolean = false, refusal: String? = null, chunkCount: Int? = null) =
        OutboxSession("s", null, 0, "UTC", 0, "en", ambient, chunkCount = chunkCount, refusal = refusal, hasAudio = hasAudio)

    @Test
    fun `what the drain sends`() {
        assertTrue(DrainPolicy.shouldSend(session()))
        assertFalse(DrainPolicy.shouldSend(session(refusal = "no")))
        assertFalse("episodes wait for audio", DrainPolicy.shouldSend(session(ambient = true)))
        assertTrue(DrainPolicy.shouldSend(session(ambient = true, hasAudio = true)))
        assertFalse(DrainPolicy.readyToFinish(session(), 0))
        assertFalse(DrainPolicy.readyToFinish(session(chunkCount = 3), 1))
        assertTrue(DrainPolicy.readyToFinish(session(chunkCount = 3), 0))
    }

    @Test
    fun `local status`() {
        assertEquals(LocalStatus.Waiting, LocalStatus.of(LocalRecording(session(), 0)))
        assertEquals(LocalStatus.Unsent(3), LocalStatus.of(LocalRecording(session(), 3)))
        assertEquals(LocalStatus.Refused("too big"), LocalStatus.of(LocalRecording(session(refusal = "too big"), 3)))
    }

    @Test
    fun `part names`() {
        assertEquals("chunk-00042.m4a", PartNames.name(42))
        assertEquals(42, PartNames.seqOf("chunk-00042.m4a"))
        assertNull(PartNames.seqOf("chunk-42.m4a"))
        assertNull(PartNames.seqOf("moment-x.m4a"))
    }
}
