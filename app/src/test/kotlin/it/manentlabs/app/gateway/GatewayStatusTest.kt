// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayStatusTest {
    private fun status(capabilities: String = """{"recordings":{"min":1,"max":1}}""", extra: String = "") =
        GatewayStatus.parse("""{"experimental":true,"capabilities":$capabilities,"uptime":12,"sources":[{"x":1}]$extra}""")

    @Test
    fun `reads the three fields and ignores the rest`() {
        val parsed = status(extra = ""","dictation":{"enabled":true,"model":"x"}""")
        assertEquals(GatewayStatus(experimental = true, recordingsOffered = true, dictationEnabled = true), parsed)
    }

    @Test
    fun `defaults when fields are absent`() {
        assertEquals(GatewayStatus(experimental = false, recordingsOffered = false, dictationEnabled = null), GatewayStatus.parse("{}"))
        assertNull(GatewayStatus.parse("""{"dictation":{}}""").dictationEnabled)
    }

    @Test
    fun `recordings need numeric bounds around version 1`() {
        assertTrue(status("""{"recordings":{"min":0,"max":3}}""").recordingsOffered)
        assertTrue(status("""{"recordings":{"min":1.0,"max":1}}""").recordingsOffered)
        assertFalse(status("""{"recordings":{"min":2,"max":3}}""").recordingsOffered)
        assertFalse(status("""{"recordings":{"min":0,"max":0}}""").recordingsOffered)
        assertFalse(status("""{"recordings":{"min":"1","max":1}}""").recordingsOffered)
        assertFalse(status("""{"recordings":{"min":1}}""").recordingsOffered)
        assertFalse(status("""{"recordings":[1,1]}""").recordingsOffered)
        assertFalse(status("""{"other":{"min":1,"max":1}}""").recordingsOffered)
        assertFalse(status("\"broken\"").recordingsOffered)
    }

    @Test
    fun `a body that is not an object fails`() {
        assertThrows(IllegalArgumentException::class.java) { GatewayStatus.parse("[]") }
        assertThrows(Exception::class.java) { GatewayStatus.parse("<html>") }
    }

    @Test
    fun `availability follows the gateway's answer`() {
        assertEquals(RecordingAvailability.AVAILABLE, GatewayStatus(true, true, null).recordingAvailability)
        assertEquals(RecordingAvailability.AVAILABLE, GatewayStatus(true, true, true).recordingAvailability)
        assertEquals(RecordingAvailability.TRANSCRIPTION_OFF, GatewayStatus(true, true, false).recordingAvailability)
        assertEquals(RecordingAvailability.UNAVAILABLE, GatewayStatus(false, true, true).recordingAvailability)
        assertEquals(RecordingAvailability.UNAVAILABLE, GatewayStatus(true, false, false).recordingAvailability)
    }

    @Test
    fun `the status line takes the first rule that fits`() {
        val error = GatewayError.Unauthorized()
        assertEquals(StatusLine.Checking, StatusLine.of(StatusCheck.Pending))
        assertEquals(StatusLine.Unreachable(error), StatusLine.of(StatusCheck.Failed(error)))
        assertEquals(StatusLine.ExperimentalOff, StatusLine.of(StatusCheck.Answered(GatewayStatus(false, false, false))))
        assertEquals(StatusLine.NoRecordings, StatusLine.of(StatusCheck.Answered(GatewayStatus(true, false, false))))
        assertEquals(StatusLine.TranscriptionOff, StatusLine.of(StatusCheck.Answered(GatewayStatus(true, true, false))))
        assertEquals(StatusLine.TakesRecordings, StatusLine.of(StatusCheck.Answered(GatewayStatus(true, true, null))))
    }

    @Test
    fun `transcription language comes from the locale's first subtag`() {
        assertEquals("it", TranscriptionLanguage.of("it-IT"))
        assertEquals("en", TranscriptionLanguage.of(" EN_us"))
        assertEquals("fil", TranscriptionLanguage.of("fil-PH"))
        assertNull(TranscriptionLanguage.of("und1-x"))
        assertNull(TranscriptionLanguage.of("e"))
        assertNull(TranscriptionLanguage.of(""))
        assertNull(TranscriptionLanguage.of(null))
    }
}
