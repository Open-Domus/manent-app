// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

import it.manentlabs.app.gateway.GatewayError

/** What the uploader sends, and what it does when the gateway says no. */
object DrainPolicy {
    sealed interface FailureAction {
        /** The gateway does not know the recording (404): open it again on the next drain. */
        data object Reopen : FailureAction

        /** Try again later. */
        data object Retry : FailureAction

        /** Never send this recording again; its audio stays on the phone. */
        data class Refuse(val reason: String) : FailureAction
    }

    /** Whether a recording has anything to send. An episode waits until a part kept audio. */
    fun shouldSend(session: OutboxSession): Boolean =
        session.refusal == null && (!session.ambient || session.hasAudio)

    fun readyToFinish(session: OutboxSession, pendingParts: Int): Boolean = session.stopped && pendingParts == 0

    fun onFailure(error: GatewayError): FailureAction = when {
        error is GatewayError.NotFound -> FailureAction.Reopen
        error.code() == GatewayError.DICTATION_DISABLED -> FailureAction.Retry
        error is GatewayError.Server && error.status in REFUSALS ->
            FailureAction.Refuse(error.body.trim().ifEmpty { "The gateway refused the recording (${error.status})." })
        else -> FailureAction.Retry
    }

    private fun GatewayError.code(): String? = when (this) {
        is GatewayError.Server -> code
        is GatewayError.Forbidden -> code
        else -> null
    }

    private val REFUSALS = setOf(400, 409, 413)
}
