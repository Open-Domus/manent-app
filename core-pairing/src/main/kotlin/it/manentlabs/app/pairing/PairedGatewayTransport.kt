// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Lorenzo Farnararo

package it.manentlabs.app.pairing

import it.manentlabs.app.transport.routing.DirectGatewayRoute
import it.manentlabs.app.transport.tls.PinnedOkHttp
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/** Every app, WebSocket, widget and narrow notification token uses the same per-pairing authority and route. */
object PairedGatewayTransport {
    fun http(pairing: Pairing, onSystemTrustVerified: (() -> Unit)? = null): OkHttpClient = create(pairing, false, onSystemTrustVerified, directRoute(pairing))
    fun streaming(pairing: Pairing, onSystemTrustVerified: (() -> Unit)? = null): OkHttpClient = create(pairing, true, onSystemTrustVerified, directRoute(pairing))

    fun httpOnRoute(pairing: Pairing, upgrade: (() -> Unit)?, route: DirectGatewayRoute?): OkHttpClient = create(pairing, false, upgrade, route)
    fun streamingOnRoute(pairing: Pairing, upgrade: (() -> Unit)?, route: DirectGatewayRoute?): OkHttpClient = create(pairing, true, upgrade, route)
    fun socket(pairing: Pairing, upgrade: (() -> Unit)?, route: DirectGatewayRoute?): OkHttpClient = create(pairing, true, upgrade, route)

    fun directRoute(pairing: Pairing): DirectGatewayRoute? = try {
        if (pairing.tlsMode == PairingTlsMode.PINNED_KEY_STRICT) DirectGatewayRoute.maintained(pairing.url, pairing.directCandidates)
        else if (pairing.tlsMode.isStrict) DirectGatewayRoute.create(pairing.url, pairing.directCandidates)
        else null
    } catch (_: Exception) { null }

    private fun create(pairing: Pairing, streaming: Boolean, upgrade: (() -> Unit)?, route: DirectGatewayRoute?): OkHttpClient {
        val url = pairing.url.toHttpUrl()
        val mode = pairing.tlsMode
        require(!mode.isStrict || url.isHttps)
        val spki = if (mode == PairingTlsMode.PINNED_KEY_STRICT) {
            PairingPayload.requireValidFingerprint(pairing.spkiFingerprint.orEmpty())
            pairing.spkiFingerprint
        } else null
        val fingerprint = if (mode == PairingTlsMode.PINNED_KEY_STRICT || mode == PairingTlsMode.SYSTEM) null else pairing.fingerprint
        val host = if (mode == PairingTlsMode.PINNED_LEAF_STRICT) null else url.host
        return if (streaming) PinnedOkHttp.buildStreaming(fingerprint, mode.requiresPin, host, upgrade, mode.isStrict, spki, route)
            else PinnedOkHttp.build(fingerprint, mode.requiresPin, host, upgrade, mode.isStrict, spki, route)
    }
}
