// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class RecordingTextTest {
    @Test
    fun `elapsed time`() {
        assertEquals("0:00", ElapsedFormat.format(0))
        assertEquals("0:59", ElapsedFormat.format(59_999))
        assertEquals("12:05", ElapsedFormat.format(725_000))
        assertEquals("59:59", ElapsedFormat.format(3_599_000))
        assertEquals("1:00:00", ElapsedFormat.format(3_600_000))
        assertEquals("4:02:09", ElapsedFormat.format(14_529_000))
    }

    @Test
    fun `list dates`() {
        val instant = Instant.parse("2026-11-24T18:16:00Z")
        val rome = ZoneId.of("Europe/Rome")
        assertEquals("Tue 24 Nov, 19:16", RecordingDates.format(instant, rome, Locale.UK))
        assertEquals("mar 24 nov, 19:16", RecordingDates.format(instant, rome, Locale.ITALY))
    }
}
