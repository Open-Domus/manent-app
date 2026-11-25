// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** JSON as the gateway speaks it: unknown fields ignored, null optionals left out. */
internal val GatewayJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/**
 * Sends [request] and hands the 2xx body to [decode]; everything else becomes
 * a [GatewayError]. Cancelling the coroutine cancels the call.
 */
internal suspend fun <T> OkHttpClient.exchange(request: Request, decode: (String) -> T): T {
    val (status, body) = try {
        newCall(request).await().use { response ->
            withContext(Dispatchers.IO) { response.code to response.body?.string().orEmpty() }
        }
    } catch (e: IOException) {
        throw GatewayError.Network(e)
    }
    if (status in 200..299) {
        return try {
            decode(body)
        } catch (e: SerializationException) {
            throw GatewayError.Decoding(e)
        } catch (e: IllegalArgumentException) {
            throw GatewayError.Decoding(e)
        }
    }
    throw classify(status, body)
}

/** Maps a non-2xx answer to its error class, reading the gateway's envelope when there is one. */
internal fun classify(status: Int, body: String): GatewayError {
    val envelope = ErrorEnvelope.parse(body)
    return when (status) {
        401 -> GatewayError.Unauthorized()
        403 -> GatewayError.Forbidden(envelope?.error, envelope?.code)
        404 -> GatewayError.NotFound(envelope?.error)
        else -> GatewayError.Server(status, envelope?.error ?: body, envelope?.code)
    }
}

/** The gateway's `{ "error": …, "code": … }` reply; anything without a non-blank `error` is not one. */
internal data class ErrorEnvelope(val error: String, val code: String?) {
    companion object {
        fun parse(body: String): ErrorEnvelope? {
            val root = try {
                Json.parseToJsonElement(body) as? JsonObject
            } catch (_: SerializationException) {
                null
            } ?: return null
            val error = root.text("error")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return ErrorEnvelope(error, root.text("code")?.takeIf { it.isNotBlank() })
        }

        private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { response.close() }
        }
    })
}
