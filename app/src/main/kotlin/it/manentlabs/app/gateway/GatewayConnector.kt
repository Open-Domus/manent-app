// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

import okhttp3.OkHttpClient
import java.security.KeyStore
import java.time.Duration
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Builds the HTTP clients for a pairing: the trust its mode asks for and the
 * timeouts each kind of call needs. All clients share one connection pool.
 */
class GatewayConnector(
    private val base: OkHttpClient,
    private val trust: GatewayTrust,
) {
    /** The client for `POST /devices/pair`, with the narrower trust of the pairing phase. */
    fun forPairing(tls: TlsTrust): OkHttpClient =
        trust.applyTo(standardBuilder(), tls, GatewayTrust.Phase.PAIRING).build()

    /** A ready-to-use API for a stored pairing. */
    fun open(pairing: Pairing): GatewayApi {
        val standard = trust.applyTo(standardBuilder(), pairing.trust, GatewayTrust.Phase.PAIRED).build()
        val clients = GatewayClients(
            standard = standard,
            upload = standard.withTimeout(UPLOAD_TIMEOUT),
            long = standard.withTimeout(LONG_TIMEOUT),
        )
        return GatewayApi(pairing.baseUrl, pairing.token, pairing.generation, pairing.deviceId, clients)
    }

    private fun standardBuilder() = base.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .readTimeout(STANDARD_TIMEOUT)
        .callTimeout(STANDARD_TIMEOUT)

    private fun OkHttpClient.withTimeout(timeout: Duration) = newBuilder()
        .callTimeout(timeout)
        .readTimeout(timeout)
        .writeTimeout(timeout)
        .build()

    companion object {
        private val CONNECT_TIMEOUT = Duration.ofSeconds(15)
        private val STANDARD_TIMEOUT = Duration.ofSeconds(30)
        private val UPLOAD_TIMEOUT = Duration.ofMinutes(2)
        private val LONG_TIMEOUT = Duration.ofMinutes(3)

        /** The connector the app uses: the system trust store, the standard hostname check, no cookies. */
        fun platform(): GatewayConnector {
            val base = OkHttpClient()
            return GatewayConnector(base, GatewayTrust(systemTrustManager(), base.hostnameVerifier))
        }

        private fun systemTrustManager(): X509TrustManager {
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(null as KeyStore?)
            return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
        }
    }
}
