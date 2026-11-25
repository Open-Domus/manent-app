// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI
import java.net.URISyntaxException

/** The rules a gateway address must meet, whether it comes from a payload, a form or storage. */
object GatewayUrl {
    /**
     * Parses [raw] as an HTTPS URL with a host and no user-info. With
     * [originOnly] it must also be a bare origin: no path beyond `/`, no query,
     * no fragment.
     */
    fun parse(raw: String, originOnly: Boolean = false): HttpUrl {
        val scheme = try {
            URI(raw).scheme
        } catch (_: URISyntaxException) {
            throw PairingInputException(PairingProblem.InvalidUrl)
        }
        if (scheme == null) throw PairingInputException(PairingProblem.InvalidUrl)
        if (!scheme.equals("https", ignoreCase = true)) throw PairingInputException(PairingProblem.NotHttps)
        val url = raw.toHttpUrlOrNull() ?: throw PairingInputException(PairingProblem.InvalidUrl)
        if (url.host.isEmpty() || url.username.isNotEmpty() || url.password.isNotEmpty()) {
            throw PairingInputException(PairingProblem.InvalidUrl)
        }
        if (originOnly && !isOrigin(url)) throw PairingInputException(PairingProblem.SystemTrustNeedsOrigin)
        return url
    }

    private fun isOrigin(url: HttpUrl) = url.encodedPath == "/" && url.encodedQuery == null && url.encodedFragment == null
}
