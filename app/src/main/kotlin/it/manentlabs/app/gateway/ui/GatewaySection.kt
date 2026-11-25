// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.manentlabs.app.R
import it.manentlabs.app.gateway.StatusLine
import it.manentlabs.app.ui.theme.Fine
import it.manentlabs.app.ui.theme.Manent
import it.manentlabs.app.ui.theme.PrimaryButton
import it.manentlabs.app.ui.theme.SecondaryButton
import it.manentlabs.app.ui.theme.Section
import it.manentlabs.app.ui.theme.StatusTone
import it.manentlabs.app.ui.theme.TextAction
import it.manentlabs.app.ui.theme.StatusLine as StatusText

/** The Omnesis gateway on Home: connect to one, or see how the connected one is doing. */
@Composable
fun GatewaySection(
    number: Int,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GatewayCardViewModel = hiltViewModel(),
) {
    val paired by viewModel.paired.collectAsStateWithLifecycle()
    val line by viewModel.statusLine.collectAsStateWithLifecycle()
    val gateway = paired

    Section(
        number = number,
        label = stringResource(R.string.home_gateway_label),
        title = stringResource(R.string.home_gateway_title),
        lede = if (gateway == null) stringResource(R.string.app_omnesis_not_connected) else null,
        modifier = modifier,
    ) {
        if (gateway == null) {
            PrimaryButton(stringResource(R.string.app_omnesis_connect), onConnect)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusText(stringResource(word(line)), tone(line))
                Text(gateway.label, style = MaterialTheme.typography.titleMedium, color = Manent.colors.ink)
                if (line != StatusLine.TakesRecordings) Fine(GatewayText.status(line))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton(stringResource(R.string.app_omnesis_check), viewModel::check)
                Spacer(Modifier.width(20.dp))
                TextAction(stringResource(R.string.app_omnesis_disconnect), viewModel::disconnect, danger = true)
            }
        }
    }
}

private fun word(line: StatusLine): Int = when (line) {
    StatusLine.Checking -> R.string.home_gateway_checking
    is StatusLine.Unreachable -> R.string.home_gateway_unreachable
    StatusLine.ExperimentalOff, StatusLine.NoRecordings, StatusLine.TranscriptionOff -> R.string.home_gateway_attention
    StatusLine.TakesRecordings -> R.string.home_gateway_ready
}

private fun tone(line: StatusLine): StatusTone = when (line) {
    StatusLine.Checking -> StatusTone.Idle
    is StatusLine.Unreachable -> StatusTone.Bad
    StatusLine.ExperimentalOff, StatusLine.NoRecordings, StatusLine.TranscriptionOff -> StatusTone.Warning
    StatusLine.TakesRecordings -> StatusTone.Good
}
