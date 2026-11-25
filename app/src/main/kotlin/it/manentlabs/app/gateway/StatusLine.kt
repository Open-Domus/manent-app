// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

/** Where the last `GET /status` for the current pairing stands. */
sealed interface StatusCheck {
    data object Pending : StatusCheck
    data class Failed(val error: GatewayError) : StatusCheck
    data class Answered(val status: GatewayStatus) : StatusCheck
}

/** The one line the Omnesis card shows under the gateway's name; the first rule that fits wins. */
sealed interface StatusLine {
    data object Checking : StatusLine
    data class Unreachable(val error: GatewayError) : StatusLine
    data object ExperimentalOff : StatusLine
    data object NoRecordings : StatusLine
    data object TranscriptionOff : StatusLine
    data object TakesRecordings : StatusLine

    companion object {
        fun of(check: StatusCheck): StatusLine = when (check) {
            StatusCheck.Pending -> Checking
            is StatusCheck.Failed -> Unreachable(check.error)
            is StatusCheck.Answered -> when {
                !check.status.experimental -> ExperimentalOff
                !check.status.recordingsOffered -> NoRecordings
                check.status.dictationEnabled == false -> TranscriptionOff
                else -> TakesRecordings
            }
        }
    }
}
