// SPDX-License-Identifier: MIT

package it.manentlabs.app.omnesis

import it.manentlabs.app.ui.statusLine
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusLineTest {
    @Test fun namesWhatKeepsRecordingsFromTheGateway() {
        assertEquals("Checking…", statusLine(null))
        assertEquals("Not reachable: timeout", statusLine(GatewayStatus(reachable = false, error = "timeout")))
        assertEquals(
            "Experimental mode is off on the gateway (OMNESIS_EXPERIMENTAL=1)",
            statusLine(GatewayStatus(reachable = true)),
        )
        assertEquals(
            "The gateway does not take recordings",
            statusLine(GatewayStatus(reachable = true, experimental = true)),
        )
        assertEquals(
            "Gateway transcription is off",
            statusLine(GatewayStatus(reachable = true, experimental = true, recordings = true, dictationEnabled = false)),
        )
        assertEquals(
            "Takes recordings",
            statusLine(GatewayStatus(reachable = true, experimental = true, recordings = true, dictationEnabled = true)),
        )
    }
}
