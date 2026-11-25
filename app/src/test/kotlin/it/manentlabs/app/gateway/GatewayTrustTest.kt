// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

import it.manentlabs.app.gateway.TlsFixtures.serve
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class GatewayTrustTest {
    private val server = MockWebServer()
    private val otherPin = "0".repeat(64)

    @After
    fun stop() = server.shutdown()

    private fun connect(
        certificate: HeldCertificate,
        trust: TlsTrust,
        host: String,
        phase: GatewayTrust.Phase = GatewayTrust.Phase.PAIRED,
    ): Int {
        server.serve(certificate)
        server.enqueue(MockResponse().setBody("ok"))
        server.start()
        val client = TlsFixtures.trust.applyTo(TlsFixtures.baseClient.newBuilder(), trust, phase)
            .callTimeout(10, TimeUnit.SECONDS).build()
        val url = server.url("/status").newBuilder().host(host).build()
        return client.newCall(Request.Builder().url(url).build()).execute().use { it.code }
    }

    private fun refused(certificate: HeldCertificate, trust: TlsTrust, host: String, phase: GatewayTrust.Phase = GatewayTrust.Phase.PAIRED) {
        assertThrows(IOException::class.java) { connect(certificate, trust, host, phase) }
    }

    @Test
    fun `legacy trusts the platform's CAs`() {
        assertEquals(200, connect(TlsFixtures.caSigned("localhost"), TlsTrust(TlsMode.LEGACY, null), "localhost"))
    }

    @Test
    fun `legacy refuses a self-signed certificate`() {
        refused(TlsFixtures.selfSigned("localhost"), TlsTrust(TlsMode.LEGACY, null), "localhost")
    }

    @Test
    fun `system checks the hostname`() {
        refused(TlsFixtures.caSigned("other.example.com"), TlsTrust(TlsMode.SYSTEM, null), "localhost")
    }

    @Test
    fun `a matching leaf pin is accepted on any host`() {
        val certificate = TlsFixtures.selfSigned("gw.lan")
        assertEquals(200, connect(certificate, TlsTrust(TlsMode.PINNED_LEAF, TlsFixtures.pinOf(certificate)), "127.0.0.1"))
    }

    @Test
    fun `a matching leaf pin is accepted while pairing`() {
        val certificate = TlsFixtures.selfSigned("gw.lan")
        val trust = TlsTrust(TlsMode.PINNED_LEAF, TlsFixtures.pinOf(certificate))
        assertEquals(200, connect(certificate, trust, "localhost", GatewayTrust.Phase.PAIRING))
    }

    @Test
    fun `a self-signed certificate with another pin is refused`() {
        refused(TlsFixtures.selfSigned("localhost"), TlsTrust(TlsMode.PINNED_LEAF, otherPin), "localhost")
    }

    @Test
    fun `pinned-leaf falls back to public trust for a DNS name its SAN covers`() {
        val certificate = TlsFixtures.caSigned("*.example.com")
        assertEquals(200, connect(certificate, TlsTrust(TlsMode.PINNED_LEAF, otherPin), "gw.example.com"))
    }

    @Test
    fun `the public fallback needs a SAN covering the host`() {
        refused(TlsFixtures.caSigned("*.example.com"), TlsTrust(TlsMode.PINNED_LEAF, otherPin), "a.b.example.com")
    }

    @Test
    fun `the public fallback is not for local hosts`() {
        refused(TlsFixtures.caSigned("localhost"), TlsTrust(TlsMode.PINNED_LEAF, otherPin), "localhost")
    }

    @Test
    fun `the public fallback is not offered while pairing`() {
        refused(TlsFixtures.caSigned("gw.example.com"), TlsTrust(TlsMode.PINNED_LEAF, otherPin), "gw.example.com", GatewayTrust.Phase.PAIRING)
    }

    @Test
    fun `strict pinning accepts the pinned leaf for its own host`() {
        val certificate = TlsFixtures.selfSigned("localhost")
        assertEquals(200, connect(certificate, TlsTrust(TlsMode.PINNED_LEAF_STRICT, TlsFixtures.pinOf(certificate)), "localhost"))
    }

    @Test
    fun `strict pinning checks the hostname`() {
        val certificate = TlsFixtures.selfSigned("gw.lan")
        refused(certificate, TlsTrust(TlsMode.PINNED_LEAF_STRICT, TlsFixtures.pinOf(certificate)), "localhost")
    }

    @Test
    fun `strict pinning checks the validity dates`() {
        val day = TimeUnit.DAYS.toMillis(1)
        val now = System.currentTimeMillis()
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost")
            .validityInterval(now - 10 * day, now - day).build()
        refused(certificate, TlsTrust(TlsMode.PINNED_LEAF_STRICT, TlsFixtures.pinOf(certificate)), "localhost")
    }

    @Test
    fun `strict pinning has no public fallback`() {
        refused(TlsFixtures.caSigned("gw.example.com"), TlsTrust(TlsMode.PINNED_LEAF_STRICT, otherPin), "gw.example.com")
    }
}
