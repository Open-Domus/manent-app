// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

import okhttp3.HttpUrl

/**
 * The phone's one pairing with a gateway: where it is, how to trust it, and
 * the token it issued. Built only from validated input or a stored credential
 * that passed the same checks.
 */
data class Pairing(
    /** The gateway URL exactly as it was given at pairing time. */
    val url: String,
    val baseUrl: HttpUrl,
    val token: String,
    /** The token id, sent back as `Omnesis-Pairing-Generation` when present. */
    val generation: String?,
    val deviceId: String,
    /** The device name the gateway gave this phone; may be empty. */
    val name: String,
    val scopes: List<String>,
    val trust: TlsTrust,
) {
    /**
     * Identifies this pairing for remembered per-pairing answers: the token id
     * when there is one, otherwise the URL and device id.
     */
    val key: String get() = generation ?: "$url\u001F$deviceId"

    override fun toString() = "Pairing(url=$url, mode=${trust.mode.wire})"
}

/** What the screens show about the pairing: nothing secret. */
data class PairedGateway(val name: String, val url: String) {
    /** The gateway's name, or its address when it gave none. */
    val label: String get() = name.ifBlank { url }
}
