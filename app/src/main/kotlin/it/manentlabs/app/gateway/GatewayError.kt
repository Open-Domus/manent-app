// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

import java.io.IOException

/**
 * Every way a gateway call can fail, told apart so callers can choose: retry,
 * give up on a recording, or tell the user. The client never retries itself.
 */
sealed class GatewayError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** No usable answer: no route, TLS refused (a pin mismatch included), timeout, reset. */
    class Network(cause: IOException) :
        GatewayError(cause.message?.takeIf { it.isNotBlank() } ?: cause::class.java.simpleName, cause)

    /** HTTP 401: the token is no longer accepted. Pairing again is the only fix. */
    class Unauthorized : GatewayError("unauthorized")

    /** HTTP 403, with the gateway's own explanation when it gave one. */
    class Forbidden(val reason: String?, val code: String?) : GatewayError(reason ?: "forbidden")

    /** HTTP 404, with the gateway's own explanation when it gave one. */
    class NotFound(val reason: String?) : GatewayError(reason ?: "not found")

    /**
     * Any other non-2xx answer. [body] is the gateway's error text, or the raw
     * body when the answer was not the gateway's error envelope.
     */
    class Server(val status: Int, val body: String, val code: String?) :
        GatewayError(body.ifBlank { "HTTP $status" })

    /** A 2xx answer whose body is not the expected shape. */
    class Decoding(cause: Throwable) : GatewayError("unexpected reply from the gateway", cause)

    companion object {
        /** Gateway code for "transcription is switched off", which callers retry later. */
        const val DICTATION_DISABLED = "DICTATION_DISABLED"
    }
}
