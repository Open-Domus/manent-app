// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable.omi

/** Where the pendant stands, as the user sees it. */
sealed interface OmiState {
    /** No pendant is associated with this phone. */
    data object NotPaired : OmiState

    /** Associated but not linked: off, out of range, held by another phone, or being retried. */
    data object Disconnected : OmiState

    /** A link was requested, or it is up and setup is running. */
    data object Connecting : OmiState

    /**
     * Linked and able to stream. [encrypted] when the pendant is bonded;
     * [canMute] when its firmware can switch the microphone off.
     */
    data class Ready(val battery: Int?, val encrypted: Boolean, val canMute: Boolean) : OmiState

    /** Linked, but the firmware sends audio in a format other than Opus 16 kHz. Not retried. */
    data class Unsupported(val codec: Int?) : OmiState
}

/** Everything the details panel shows. A null field was not reported. */
data class OmiDetails(
    val rssi: Int? = null,
    val name: String? = null,
    val address: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val hardware: String? = null,
    val firmware: String? = null,
    val serial: String? = null,
    val battery: Int? = null,
    val charging: Boolean? = null,
    val encrypted: Boolean = false,
    val mtu: Int? = null,
    val ledDim: Int? = null,
    val micGain: Int? = null,
    val canMute: Boolean = false,
    val features: List<OmiFeature>? = null,
    val storage: OmiStorage? = null,
    /** Only while a recording streams. */
    val framesLost: Int? = null,
)
