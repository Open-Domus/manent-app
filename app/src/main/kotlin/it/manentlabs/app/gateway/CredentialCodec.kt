// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

/**
 * The stored form of a [Pairing]: the JSON object under `gateway.credential.v1`.
 * Field names and values are those installed builds already hold, so a pairing
 * made before this release still opens. Fields this app does not use are
 * tolerated on read.
 */
object CredentialCodec {
    fun encode(pairing: Pairing): String = GatewayJson.encodeToString(
        StoredCredential.serializer(),
        StoredCredential(
            url = pairing.url,
            token = pairing.token,
            pairingGeneration = pairing.generation,
            deviceId = pairing.deviceId,
            name = pairing.name,
            scopes = pairing.scopes,
            tlsMode = pairing.trust.mode.wire,
            fingerprint = pairing.trust.fingerprint,
        ),
    )

    /**
     * Reads a stored credential, or null when it cannot be trusted as a pairing:
     * not JSON, not HTTPS, a malformed pin, a `system` URL that is not an origin,
     * or a trust mode this app does not know.
     */
    fun decode(text: String): Pairing? {
        val stored = try {
            GatewayJson.decodeFromString(StoredCredential.serializer(), text)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        val mode = TlsMode.fromWire(stored.tlsMode) ?: return null
        val pin = if (mode.needsPin) stored.fingerprint?.takeIf(Fingerprint::isValid) ?: return null else null
        val baseUrl = try {
            GatewayUrl.parse(stored.url, originOnly = mode == TlsMode.SYSTEM)
        } catch (_: PairingInputException) {
            return null
        }
        return Pairing(
            url = stored.url,
            baseUrl = baseUrl,
            token = stored.token,
            generation = stored.pairingGeneration?.takeIf { it.isNotBlank() },
            deviceId = stored.deviceId,
            name = stored.name,
            scopes = stored.scopes,
            trust = TlsTrust(mode, pin),
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    private data class StoredCredential(
        val url: String,
        val token: String,
        val pairingGeneration: String? = null,
        @EncodeDefault val accountId: String = "local",
        val deviceId: String,
        @EncodeDefault val name: String = "",
        @EncodeDefault val scopes: List<String> = emptyList(),
        val tlsMode: String,
        val fingerprint: String? = null,
        // Written for builds that expect them; this app never reads them.
        @EncodeDefault val directCandidates: JsonArray = JsonArray(emptyList()),
        @EncodeDefault val deviceKeyRecorded: Boolean = false,
    )
}
