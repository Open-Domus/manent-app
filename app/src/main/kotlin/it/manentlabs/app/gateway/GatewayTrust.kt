// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

import okhttp3.OkHttpClient
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSession
import javax.net.ssl.X509TrustManager

/**
 * Turns a [TlsTrust] into OkHttp's certificate and hostname checks.
 *
 * [platform] is the system trust store and [platformVerifier] the standard
 * hostname check; both are parameters so tests can stand in a private CA.
 */
class GatewayTrust(
    private val platform: X509TrustManager,
    private val platformVerifier: HostnameVerifier,
) {
    /** The two moments that trust differs: the pairing exchange, and everything after it. */
    enum class Phase { PAIRING, PAIRED }

    fun applyTo(builder: OkHttpClient.Builder, trust: TlsTrust, phase: Phase): OkHttpClient.Builder {
        return when (trust.mode) {
            TlsMode.LEGACY, TlsMode.SYSTEM -> builder.sslSocketFactory(context(platform).socketFactory, platform)
                .hostnameVerifier(platformVerifier)
            TlsMode.PINNED_LEAF -> {
                val pin = requirePin(trust.fingerprint)
                val publicFallback = phase == Phase.PAIRED
                val manager = PinnedTrustManager(pin, checkDates = false, fallback = platform.takeIf { publicFallback })
                builder.sslSocketFactory(context(manager).socketFactory, manager)
                    .hostnameVerifier(PinnedLeafVerifier(pin, publicFallback))
            }
            TlsMode.PINNED_LEAF_STRICT -> {
                val manager = PinnedTrustManager(requirePin(trust.fingerprint), checkDates = true, fallback = null)
                builder.sslSocketFactory(context(manager).socketFactory, manager)
                    .hostnameVerifier(platformVerifier)
            }
        }
    }

    private fun context(manager: X509TrustManager) =
        SSLContext.getInstance("TLS").apply { init(null, arrayOf(manager), null) }

    private fun requirePin(pin: String?): String = requireNotNull(pin) { "A pinned mode needs a fingerprint" }

    /**
     * The `pinned-leaf` host check: a pinned leaf is good for any host; any
     * other leaf (which the platform has already accepted) must be for a DNS
     * name that one of its DNS SANs covers.
     */
    private class PinnedLeafVerifier(private val pin: String, private val publicFallback: Boolean) : HostnameVerifier {
        override fun verify(host: String, session: SSLSession): Boolean {
            val leaf = try {
                session.peerCertificates.firstOrNull() as? X509Certificate
            } catch (_: SSLPeerUnverifiedException) {
                null
            } ?: return false
            if (Fingerprint.sha256Hex(leaf.encoded) == pin) return true
            return publicFallback && HostNames.qualifiesForPublicTrust(host) && HostNames.certificateCovers(leaf, host)
        }
    }
}
