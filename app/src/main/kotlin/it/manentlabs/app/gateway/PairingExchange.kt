// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * `POST /devices/pair`: trades a one-time pairing code for this phone's token.
 * No token exists yet, so the request carries no `Authorization`.
 */
internal object PairingExchange {
    private val JSON = "application/json; charset=utf-8".toMediaType()

    /**
     * @param previousDeviceId the device id of the pairing being replaced, so
     *   the gateway can retire it.
     * @throws GatewayError when the gateway is unreachable or refuses the code.
     */
    suspend fun pair(client: OkHttpClient, target: PairingTarget, installId: String, previousDeviceId: String?): Pairing {
        val baseUrl = GatewayUrl.parse(target.url, originOnly = target.trust.mode == TlsMode.SYSTEM)
        val body = PairRequest(
            pairingCode = target.pairingCode,
            capabilities = Capabilities(
                suggestedName = "Manent-${installId.take(6)}",
                installId = installId,
                previousDeviceId = previousDeviceId,
            ),
        )
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegment("devices").addPathSegment("pair").build())
            .header("Accept", "application/json")
            .post(GatewayJson.encodeToString(PairRequest.serializer(), body).toRequestBody(JSON))
            .build()
        val reply = client.exchange(request) { GatewayJson.decodeFromString(PairReply.serializer(), it) }
        return Pairing(
            url = target.url,
            baseUrl = baseUrl,
            token = reply.token,
            generation = reply.tokenId?.takeIf { it.isNotBlank() },
            deviceId = reply.device.id,
            name = reply.device.name,
            scopes = reply.scopes,
            trust = target.trust,
        )
    }

    @Serializable
    private data class PairRequest(val pairingCode: String, val capabilities: Capabilities)

    /** What the phone says about itself. It hosts no sources, so the collections are empty. */
    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    private data class Capabilities(
        @EncodeDefault val platform: String = "android",
        @EncodeDefault val hostname: String = "Manent",
        val suggestedName: String,
        val installId: String,
        val previousDeviceId: String? = null,
        @EncodeDefault val hostableSourceTypes: List<String> = emptyList(),
        @EncodeDefault val pushBasedSourceTypes: List<String> = emptyList(),
        @EncodeDefault val multiDeviceModes: Map<String, String> = emptyMap(),
    )

    @Serializable
    private data class PairReply(
        val token: String,
        val tokenId: String? = null,
        val device: Device,
        val scopes: List<String> = emptyList(),
    )

    @Serializable
    private data class Device(val id: String, val name: String = "")
}
