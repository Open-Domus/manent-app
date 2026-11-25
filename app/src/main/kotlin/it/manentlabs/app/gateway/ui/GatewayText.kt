// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import it.manentlabs.app.R
import it.manentlabs.app.gateway.GatewayError
import it.manentlabs.app.gateway.PairingProblem
import it.manentlabs.app.gateway.StatusLine

/** The user's words for gateway states and failures. */
internal object GatewayText {
    private const val MAX_GATEWAY_TEXT = 200

    /**
     * A short reason for a failed call. The gateway's own explanation is shown
     * when it gave one; a raw page (an HTML error from a proxy) is not.
     */
    @Composable
    fun error(error: GatewayError): String = when (error) {
        is GatewayError.Network -> error.message.orEmpty()
        is GatewayError.Unauthorized -> stringResource(R.string.gateway_error_unauthorized)
        is GatewayError.Forbidden -> error.reason ?: stringResource(R.string.gateway_error_forbidden)
        is GatewayError.NotFound -> error.reason ?: stringResource(R.string.gateway_error_not_found)
        is GatewayError.Server -> error.body.trim().takeIf(::readable)
            ?: stringResource(R.string.gateway_error_server, error.status)
        is GatewayError.Decoding -> stringResource(R.string.gateway_error_decoding)
    }

    @Composable
    fun problem(problem: PairingProblem): String = when (problem) {
        PairingProblem.NotJson -> stringResource(R.string.gateway_problem_not_json)
        PairingProblem.MissingVersion -> stringResource(R.string.gateway_problem_missing_version)
        is PairingProblem.UnsupportedVersion -> stringResource(R.string.gateway_problem_unsupported_version, problem.version)
        is PairingProblem.MissingField ->
            if (problem.name == "pairingCode") {
                stringResource(R.string.gateway_problem_missing_code)
            } else {
                stringResource(R.string.gateway_problem_missing_field, problem.name)
            }
        PairingProblem.NotHttps -> stringResource(R.string.gateway_problem_not_https)
        PairingProblem.InvalidUrl -> stringResource(R.string.gateway_problem_invalid_url)
        PairingProblem.SystemTrustNeedsOrigin -> stringResource(R.string.gateway_problem_system_origin)
        PairingProblem.InvalidFingerprint -> stringResource(R.string.gateway_problem_invalid_fingerprint)
        PairingProblem.InvalidTlsMode -> stringResource(R.string.gateway_problem_invalid_tls_mode)
        is PairingProblem.UnsupportedTlsMode -> stringResource(R.string.gateway_problem_unsupported_tls_mode, problem.mode)
    }

    @Composable
    fun status(line: StatusLine): String = when (line) {
        StatusLine.Checking -> stringResource(R.string.app_status_checking)
        is StatusLine.Unreachable -> stringResource(R.string.app_status_unreachable, error(line.error))
        StatusLine.ExperimentalOff -> stringResource(R.string.app_status_experimental_off)
        StatusLine.NoRecordings -> stringResource(R.string.app_status_no_recordings)
        StatusLine.TranscriptionOff -> stringResource(R.string.app_status_transcription_off)
        StatusLine.TakesRecordings -> stringResource(R.string.app_status_takes_recordings)
    }

    private fun readable(text: String) = text.isNotEmpty() && text.length <= MAX_GATEWAY_TEXT && !text.startsWith("<")
}
