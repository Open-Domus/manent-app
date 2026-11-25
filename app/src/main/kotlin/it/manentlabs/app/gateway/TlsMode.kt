// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

/**
 * How the app decides to trust the gateway's certificate. [wire] is the name
 * used in pairing payloads and in the stored credential.
 *
 * `pinned-key-strict` exists on the gateway side but only comes out of the
 * cloud-relay flow, which Manent does not offer, so it has no entry here.
 */
enum class TlsMode(val wire: String, val needsPin: Boolean) {
    /** Platform CAs and hostname: what a v2 payload or a pin-less manual entry gets. */
    LEGACY("legacy", needsPin = false),

    /** Platform CAs and hostname, for a gateway with a public certificate. */
    SYSTEM("system", needsPin = false),

    /** The pinned leaf certificate, or a publicly trusted certificate for a DNS name. */
    PINNED_LEAF("pinned-leaf", needsPin = true),

    /** The pinned leaf certificate, in date and valid for the host. */
    PINNED_LEAF_STRICT("pinned-leaf-strict", needsPin = true),
    ;

    companion object {
        fun fromWire(name: String): TlsMode? = entries.firstOrNull { it.wire == name }
    }
}
