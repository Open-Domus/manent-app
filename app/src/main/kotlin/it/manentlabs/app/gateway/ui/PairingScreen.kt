// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.manentlabs.app.R
import it.manentlabs.app.gateway.PairingResult
import it.manentlabs.app.ui.theme.Field
import it.manentlabs.app.ui.theme.Fine
import it.manentlabs.app.ui.theme.Manent
import it.manentlabs.app.ui.theme.ManentPage
import it.manentlabs.app.ui.theme.Panel
import it.manentlabs.app.ui.theme.PrimaryButton
import it.manentlabs.app.ui.theme.SecondaryButton
import it.manentlabs.app.ui.theme.Section
import it.manentlabs.app.ui.theme.StatusLine
import it.manentlabs.app.ui.theme.StatusTone

/**
 * Connects the phone to an Omnesis gateway: paste the payload the gateway's
 * pairing command prints, or type its address and code. Closes on success.
 */
@Composable
fun PairingScreen(onBack: () -> Unit, viewModel: PairingViewModel = hiltViewModel()) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val outcome by viewModel.outcome.collectAsStateWithLifecycle()
    var payload by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var fingerprint by rememberSaveable { mutableStateOf("") }
    var byHand by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(outcome) {
        if (outcome is PairingResult.Paired) onBack()
    }

    ManentPage(title = stringResource(R.string.app_pair_title), onBack = onBack) {
        Section(
            number = 1,
            label = stringResource(R.string.gateway_pair_payload_label),
            title = stringResource(R.string.gateway_pair_payload_title),
            lede = stringResource(R.string.app_pair_instructions),
        ) {
            Panel(contentPadding = PaddingValues(14.dp)) {
                SelectionContainer {
                    Text(stringResource(R.string.gateway_pair_command), style = MaterialTheme.typography.labelMedium, color = Manent.colors.ink)
                }
            }
            Field(
                value = payload,
                onValueChange = { payload = it },
                label = stringResource(R.string.app_pair_payload_label),
                hint = stringResource(R.string.gateway_pair_payload_hint),
                singleLine = false,
                minLines = 3,
                enabled = !busy,
                textStyle = MaterialTheme.typography.labelMedium,
            )
            PrimaryButton(
                stringResource(R.string.app_pair_from_payload),
                onClick = {
                    byHand = false
                    viewModel.pairFromPayload(payload)
                },
                enabled = !busy && payload.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (!byHand) Attempt(busy, outcome)
        }

        Section(
            number = 2,
            label = stringResource(R.string.app_pair_by_hand),
            title = stringResource(R.string.gateway_pair_manual_title),
        ) {
            Field(
                value = url,
                onValueChange = { url = it },
                label = stringResource(R.string.app_pair_gateway_url),
                placeholder = "https://",
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Field(value = code, onValueChange = { code = it }, label = stringResource(R.string.app_pair_code), enabled = !busy)
            Field(
                value = fingerprint,
                onValueChange = { fingerprint = it },
                label = stringResource(R.string.app_pair_fingerprint),
                enabled = !busy,
                textStyle = MaterialTheme.typography.labelMedium,
            )
            SecondaryButton(
                stringResource(R.string.app_pair),
                onClick = {
                    byHand = true
                    viewModel.pairManually(url, code, fingerprint)
                },
                enabled = !busy && url.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (byHand) Attempt(busy, outcome)
        }
    }
}

/** The attempt under the button that started it: in progress, or why it failed. */
@Composable
private fun Attempt(busy: Boolean, outcome: PairingResult?) {
    if (busy) StatusLine(stringResource(R.string.gateway_pairing), StatusTone.Live)
    when (outcome) {
        is PairingResult.Invalid -> Fine(GatewayText.problem(outcome.problem), color = Manent.colors.danger)
        is PairingResult.Failed -> Fine(stringResource(R.string.gateway_pair_failed, GatewayText.error(outcome.error)), color = Manent.colors.danger)
        PairingResult.NotSaved -> Fine(stringResource(R.string.gateway_pair_not_saved), color = Manent.colors.danger)
        else -> Unit
    }
}
