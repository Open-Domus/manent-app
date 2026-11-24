// SPDX-License-Identifier: MIT

package it.manentlabs.app.omnesis

import it.manentlabs.app.R
import it.manentlabs.app.ui.StatusLine
import it.manentlabs.app.ui.statusLine
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusLineTest {
    @Test fun namesWhatKeepsRecordingsFromTheGateway() {
        assertEquals(StatusLine(R.string.app_status_checking), statusLine(null))
        assertEquals(
            StatusLine(R.string.app_status_unreachable, "timeout"),
            statusLine(GatewayStatus(reachable = false, error = "timeout")),
        )
        assertEquals(
            StatusLine(R.string.app_status_experimental_off),
            statusLine(GatewayStatus(reachable = true)),
        )
        assertEquals(
            StatusLine(R.string.app_status_no_recordings),
            statusLine(GatewayStatus(reachable = true, experimental = true)),
        )
        assertEquals(
            StatusLine(R.string.app_status_transcription_off),
            statusLine(GatewayStatus(reachable = true, experimental = true, recordings = true, dictationEnabled = false)),
        )
        assertEquals(
            StatusLine(R.string.app_status_takes_recordings),
            statusLine(GatewayStatus(reachable = true, experimental = true, recordings = true, dictationEnabled = true)),
        )
    }
}
