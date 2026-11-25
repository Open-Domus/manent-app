// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import java.net.InetAddress

/**
 * Certificates for TLS tests: a private CA that stands in for the platform
 * trust store, and helpers to serve a certificate from a MockWebServer.
 */
object TlsFixtures {
    val ca: HeldCertificate = HeldCertificate.Builder().certificateAuthority(0).commonName("Test CA").build()

    /** The "platform" trust store of the tests: it trusts [ca] only. */
    val platform = HandshakeCertificates.Builder().addTrustedCertificate(ca.certificate).build().trustManager

    /** Names the tests use for public hosts; they all resolve to this machine. */
    private val testHosts = setOf("gw.example.com", "a.b.example.com")

    /** A base client whose DNS sends [testHosts] to the loopback address. */
    val baseClient: OkHttpClient = OkHttpClient.Builder()
        .dns(
            object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    if (hostname in testHosts) listOf(InetAddress.getLoopbackAddress()) else Dns.SYSTEM.lookup(hostname)
            },
        )
        .build()

    val trust = GatewayTrust(platform, baseClient.hostnameVerifier)

    fun selfSigned(vararg names: String): HeldCertificate =
        HeldCertificate.Builder().apply { names.forEach { addSubjectAlternativeName(it) } }.build()

    fun caSigned(vararg names: String): HeldCertificate =
        HeldCertificate.Builder().apply { names.forEach { addSubjectAlternativeName(it) } }.signedBy(ca).build()

    fun pinOf(certificate: HeldCertificate): String = Fingerprint.sha256Hex(certificate.certificate.encoded)

    /** Serves [certificate] (with the CA as its chain when it was issued by it). */
    fun MockWebServer.serve(certificate: HeldCertificate) {
        val issuedByCa = certificate.certificate.issuerX500Principal == ca.certificate.subjectX500Principal
        val intermediates = if (issuedByCa) arrayOf(ca.certificate) else emptyArray()
        val chain = HandshakeCertificates.Builder().heldCertificate(certificate, *intermediates).build()
        useHttps(chain.sslSocketFactory(), false)
    }
}
