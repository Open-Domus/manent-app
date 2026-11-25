// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

/**
 * Accepts the server whose leaf certificate hashes to [pin]. A self-signed
 * gateway on a LAN address passes this way, since no CA chain is asked for.
 *
 * With [checkDates] the pinned leaf must also be inside its validity period.
 * With a [fallback], a chain the platform trusts is let through as well; the
 * host check for that case happens in the hostname verifier, which knows the
 * host.
 */
internal class PinnedTrustManager(
    private val pin: String,
    private val checkDates: Boolean,
    private val fallback: X509TrustManager?,
) : X509ExtendedTrustManager() {

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        if (pinMatches(chain)) return
        fallback?.checkServerTrusted(chain, authType) ?: throw mismatch()
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) {
        if (pinMatches(chain)) return
        when (fallback) {
            null -> throw mismatch()
            is X509ExtendedTrustManager -> fallback.checkServerTrusted(chain, authType, socket)
            else -> fallback.checkServerTrusted(chain, authType)
        }
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) {
        if (pinMatches(chain)) return
        when (fallback) {
            null -> throw mismatch()
            is X509ExtendedTrustManager -> fallback.checkServerTrusted(chain, authType, engine)
            else -> fallback.checkServerTrusted(chain, authType)
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = fallback?.acceptedIssuers ?: emptyArray()

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = refuseClient()
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) = refuseClient()
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) = refuseClient()

    private fun pinMatches(chain: Array<X509Certificate>): Boolean {
        val leaf = chain.firstOrNull() ?: throw CertificateException("The server sent no certificate")
        if (Fingerprint.sha256Hex(leaf.encoded) != pin) return false
        if (checkDates) leaf.checkValidity()
        return true
    }

    private fun mismatch() = CertificateException("The certificate does not match the pinned fingerprint")

    private fun refuseClient(): Nothing = throw CertificateException("Client certificates are not trusted here")
}
