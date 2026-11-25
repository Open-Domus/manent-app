// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PairingTargetTest {
    private val pin = "ab".repeat(32)

    private fun problem(block: () -> Unit): PairingProblem =
        try {
            block()
            throw AssertionError("expected a pairing problem")
        } catch (e: PairingInputException) {
            e.problem
        }

    @Test
    fun `v2 pairs without a pin`() {
        val target = PairingTarget.fromPayload("""  {"v":2,"gatewayUrl":"https://gw.lan:8443/base","pairingCode":"ABC","extra":1} """)
        assertEquals("https://gw.lan:8443/base", target.url)
        assertEquals("ABC", target.pairingCode)
        assertEquals(TlsTrust(TlsMode.LEGACY, null), target.trust)
    }

    @Test
    fun `v3 pins the leaf`() {
        val target = PairingTarget.fromPayload("""{"v":3,"gatewayUrl":"https://10.0.0.2","pairingCode":"c","fingerprint":"$pin"}""")
        assertEquals(TlsTrust(TlsMode.PINNED_LEAF, pin), target.trust)
    }

    @Test
    fun `v4 reads each supported mode`() {
        fun v4(tls: String) = PairingTarget.fromPayload("""{"v":4,"gatewayUrl":"https://gw.example.com","pairingCode":"c","tls":$tls}""").trust
        assertEquals(TlsTrust(TlsMode.SYSTEM, null), v4("""{"mode":"system","fingerprint":"$pin"}"""))
        assertEquals(TlsTrust(TlsMode.PINNED_LEAF, pin), v4("""{"mode":"pinned-leaf","fingerprint":"$pin"}"""))
        assertEquals(TlsTrust(TlsMode.PINNED_LEAF_STRICT, pin), v4("""{"mode":"pinned-leaf-strict","fingerprint":"$pin"}"""))
    }

    @Test
    fun `v4 refuses modes it cannot honour`() {
        fun v4(tls: String) = problem {
            PairingTarget.fromPayload("""{"v":4,"gatewayUrl":"https://gw.example.com","pairingCode":"c","tls":$tls}""")
        }
        assertEquals(PairingProblem.UnsupportedTlsMode("pinned-key-strict"), v4("""{"mode":"pinned-key-strict","spkiFingerprint":"$pin"}"""))
        assertEquals(PairingProblem.InvalidTlsMode, v4("""{"mode":"trust-me"}"""))
        assertEquals(PairingProblem.MissingField("tls.mode"), v4("{}"))
        assertEquals(PairingProblem.MissingField("tls.fingerprint"), v4("""{"mode":"pinned-leaf"}"""))
        assertEquals(PairingProblem.MissingField("tls"), problem {
            PairingTarget.fromPayload("""{"v":4,"gatewayUrl":"https://gw.example.com","pairingCode":"c"}""")
        })
    }

    @Test
    fun `system trust needs a bare origin`() {
        fun system(url: String) = PairingTarget.fromPayload("""{"v":4,"gatewayUrl":"$url","pairingCode":"c","tls":{"mode":"system"}}""")
        assertEquals("https://gw.example.com/", system("https://gw.example.com/").url)
        for (url in listOf("https://gw.example.com/api", "https://gw.example.com/?a=1", "https://gw.example.com/#x")) {
            assertEquals(url, PairingProblem.SystemTrustNeedsOrigin, problem { system(url) })
        }
    }

    @Test
    fun `versions other than 2 to 4 are refused`() {
        assertEquals(PairingProblem.UnsupportedVersion("1"), problem {
            PairingTarget.fromPayload("""{"v":1,"url":"https://gw","token":"t","accountId":"a"}""")
        })
        assertEquals(PairingProblem.UnsupportedVersion("5"), problem { PairingTarget.fromPayload("""{"v":5}""") })
        assertEquals(PairingProblem.UnsupportedVersion("2"), problem { PairingTarget.fromPayload("""{"v":"2"}""") })
        assertEquals(PairingProblem.MissingVersion, problem { PairingTarget.fromPayload("""{"gatewayUrl":"https://gw"}""") })
    }

    @Test
    fun `malformed payloads are refused`() {
        assertEquals(PairingProblem.NotJson, problem { PairingTarget.fromPayload("not json") })
        assertEquals(PairingProblem.NotJson, problem { PairingTarget.fromPayload("[1,2]") })
        assertEquals(PairingProblem.MissingField("gatewayUrl"), problem { PairingTarget.fromPayload("""{"v":2,"pairingCode":"c"}""") })
        assertEquals(PairingProblem.MissingField("pairingCode"), problem { PairingTarget.fromPayload("""{"v":2,"gatewayUrl":"https://gw"}""") })
    }

    @Test
    fun `payload pins must already be lowercase`() {
        assertEquals(PairingProblem.InvalidFingerprint, problem {
            PairingTarget.fromPayload("""{"v":3,"gatewayUrl":"https://gw","pairingCode":"c","fingerprint":"${pin.uppercase()}"}""")
        })
    }

    @Test
    fun `urls must be https with a host and no user-info`() {
        fun url(value: String) = problem { PairingTarget.fromPayload("""{"v":2,"gatewayUrl":"$value","pairingCode":"c"}""") }
        assertEquals(PairingProblem.NotHttps, url("http://gw.lan"))
        assertEquals(PairingProblem.NotHttps, url("ftp://gw.lan"))
        assertEquals(PairingProblem.InvalidUrl, url("gw.lan"))
        assertEquals(PairingProblem.InvalidUrl, url("https://user:pw@gw.lan"))
        assertEquals(PairingProblem.InvalidUrl, url("https://"))
        assertEquals("HTTPS://gw.lan", PairingTarget.fromPayload("""{"v":2,"gatewayUrl":"HTTPS://gw.lan","pairingCode":"c"}""").url)
    }

    @Test
    fun `manual entry trims, lowercases the pin and picks the mode`() {
        val plain = PairingTarget.fromManualEntry("  https://gw.lan ", " 123 ", "   ")
        assertEquals("https://gw.lan", plain.url)
        assertEquals("123", plain.pairingCode)
        assertEquals(TlsMode.LEGACY, plain.trust.mode)
        assertNull(plain.trust.fingerprint)

        val pinned = PairingTarget.fromManualEntry("https://gw.lan", "123", " ${pin.uppercase()} ")
        assertEquals(TlsTrust(TlsMode.PINNED_LEAF, pin), pinned.trust)
    }

    @Test
    fun `manual entry refuses bad input`() {
        assertEquals(PairingProblem.InvalidFingerprint, problem { PairingTarget.fromManualEntry("https://gw", "1", "ab:cd") })
        assertEquals(PairingProblem.MissingField("pairingCode"), problem { PairingTarget.fromManualEntry("https://gw", " ", "") })
        assertEquals(PairingProblem.NotHttps, problem { PairingTarget.fromManualEntry("http://gw", "1", "") })
        assertEquals(PairingProblem.InvalidUrl, problem { PairingTarget.fromManualEntry("", "1", "") })
    }

    @Test
    fun `descriptions leave the pin out`() {
        val target = PairingTarget.fromManualEntry("https://gw.lan", "secret-code", pin)
        assertFalse(pin in target.toString() || "secret-code" in target.toString())
    }
}
