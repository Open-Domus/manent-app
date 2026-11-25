// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

/** Why a pairing payload or a hand-typed pairing cannot be used, before anything is sent. */
sealed interface PairingProblem {
    data object NotJson : PairingProblem
    data object MissingVersion : PairingProblem
    data class UnsupportedVersion(val version: String) : PairingProblem
    data class MissingField(val name: String) : PairingProblem
    data object NotHttps : PairingProblem
    data object InvalidUrl : PairingProblem
    data object SystemTrustNeedsOrigin : PairingProblem
    data object InvalidFingerprint : PairingProblem
    data object InvalidTlsMode : PairingProblem
    data class UnsupportedTlsMode(val mode: String) : PairingProblem
}

/** Carries a [PairingProblem] out of the validators; the message is English, for logs and tests. */
class PairingInputException(val problem: PairingProblem) : Exception(problem.toString())
