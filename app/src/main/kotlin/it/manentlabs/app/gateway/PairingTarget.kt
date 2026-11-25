// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * A validated request to pair: where the gateway is, the one-time code, and
 * how its certificate is to be trusted. [url] is kept as given, since that is
 * what gets stored.
 */
data class PairingTarget(
    val url: String,
    val pairingCode: String,
    val trust: TlsTrust,
) {
    override fun toString() = "PairingTarget(url=$url, mode=${trust.mode.wire})"

    companion object {
        /**
         * Reads a pasted or scanned payload. Versions 2, 3 and 4 are accepted;
         * keys a version does not define are ignored.
         *
         * @throws PairingInputException when the payload cannot be used.
         */
        fun fromPayload(text: String): PairingTarget {
            val root = try {
                Json.parseToJsonElement(text.trim()) as? JsonObject
            } catch (_: SerializationException) {
                null
            } ?: fail(PairingProblem.NotJson)

            val version = root["v"] ?: fail(PairingProblem.MissingVersion)
            val number = (version as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
            return when (number) {
                2 -> build(root, TlsTrust(TlsMode.LEGACY, null))
                3 -> build(root, TlsTrust(TlsMode.PINNED_LEAF, payloadPin(root.string("fingerprint"), "fingerprint")))
                4 -> build(root, v4Trust(root))
                else -> fail(PairingProblem.UnsupportedVersion(versionText(version)))
            }
        }

        /**
         * Reads the "by hand" form. Every field is trimmed and a blank
         * fingerprint means none: then the platform's trust applies, otherwise
         * the fingerprint is pinned.
         */
        fun fromManualEntry(url: String, pairingCode: String, fingerprint: String): PairingTarget {
            val pin = fingerprint.trim().lowercase().ifEmpty { null }
            if (pin != null && !Fingerprint.isValid(pin)) fail(PairingProblem.InvalidFingerprint)
            val trust = if (pin == null) TlsTrust(TlsMode.LEGACY, null) else TlsTrust(TlsMode.PINNED_LEAF, pin)
            val code = pairingCode.trim().ifEmpty { fail(PairingProblem.MissingField("pairingCode")) }
            val address = url.trim()
            GatewayUrl.parse(address)
            return PairingTarget(address, code, trust)
        }

        private fun build(root: JsonObject, trust: TlsTrust): PairingTarget {
            val url = root.string("gatewayUrl") ?: fail(PairingProblem.MissingField("gatewayUrl"))
            val code = root.string("pairingCode")?.takeIf { it.isNotBlank() }
                ?: fail(PairingProblem.MissingField("pairingCode"))
            GatewayUrl.parse(url, originOnly = trust.mode == TlsMode.SYSTEM)
            return PairingTarget(url, code, trust)
        }

        private fun v4Trust(root: JsonObject): TlsTrust {
            val tls = root["tls"] as? JsonObject ?: fail(PairingProblem.MissingField("tls"))
            val mode = tls.string("mode") ?: fail(PairingProblem.MissingField("tls.mode"))
            return when (mode) {
                TlsMode.SYSTEM.wire -> TlsTrust(TlsMode.SYSTEM, null)
                TlsMode.PINNED_LEAF.wire, TlsMode.PINNED_LEAF_STRICT.wire ->
                    TlsTrust(TlsMode.fromWire(mode)!!, payloadPin(tls.string("fingerprint"), "tls.fingerprint"))
                PINNED_KEY_STRICT -> fail(PairingProblem.UnsupportedTlsMode(mode))
                else -> fail(PairingProblem.InvalidTlsMode)
            }
        }

        /** Payload pins are checked as given: an uppercase pin is a malformed pin. */
        private fun payloadPin(value: String?, field: String): String {
            val pin = value ?: fail(PairingProblem.MissingField(field))
            if (!Fingerprint.isValid(pin)) fail(PairingProblem.InvalidFingerprint)
            return pin
        }

        private fun versionText(version: JsonElement) =
            (version as? JsonPrimitive)?.content ?: version.toString()

        private fun JsonObject.string(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

        private fun fail(problem: PairingProblem): Nothing = throw PairingInputException(problem)

        private const val PINNED_KEY_STRICT = "pinned-key-strict"
    }
}

/** A trust mode with its pin, when the mode has one. */
data class TlsTrust(val mode: TlsMode, val fingerprint: String?) {
    override fun toString() = "TlsTrust(${mode.wire})"
}
