// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

/** How a pairing attempt ended. */
sealed interface PairingResult {
    data class Paired(val gateway: PairedGateway) : PairingResult

    /** The input was refused before anything was sent. */
    data class Invalid(val problem: PairingProblem) : PairingResult

    /** The gateway could not be reached or refused the code. */
    data class Failed(val error: GatewayError) : PairingResult

    /** Paired, but this phone's Keystore would not keep the credential. */
    data object NotSaved : PairingResult

    /** A newer attempt, or an unpair, came first; this one stored nothing. */
    data object Superseded : PairingResult
}

/** Why voice commands cannot use the gateway transcriber right now. */
enum class TranscriberBlock { NOT_PAIRED, DICTATION_OFF }
