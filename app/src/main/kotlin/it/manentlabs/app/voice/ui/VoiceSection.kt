// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.manentlabs.app.R
import it.manentlabs.app.ui.theme.Fine
import it.manentlabs.app.ui.theme.Hairline
import it.manentlabs.app.ui.theme.Manent
import it.manentlabs.app.ui.theme.SecondaryButton
import it.manentlabs.app.ui.theme.Section
import it.manentlabs.app.ui.theme.StatusLine
import it.manentlabs.app.ui.theme.StatusTone

/** Voice commands on Home: whether they listen, which word goes where, and the way into their settings. */
@Composable
fun VoiceSection(
    number: Int,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VoiceCommandsViewModel = hiltViewModel(),
) {
    val card by viewModel.card.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    Section(
        number = number,
        label = stringResource(R.string.home_voice_label),
        title = stringResource(R.string.voice_title),
        modifier = modifier,
    ) {
        val c = card
        if (c != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    !c.anyActive -> StatusLine(stringResource(R.string.voice_status_needs_setup), StatusTone.Warning)
                    c.config.enabled -> StatusLine(stringResource(R.string.voice_status_listening), StatusTone.Live)
                    else -> StatusLine(stringResource(R.string.voice_status_off), StatusTone.Idle)
                }
                Text(summary(c), style = MaterialTheme.typography.bodyLarge, color = Manent.colors.ink)
            }
            Column(Modifier.fillMaxWidth()) {
                Hairline()
                ListenToggle(c, viewModel::setEnabled)
                Hairline()
            }
            for (row in c.rows) {
                val problem = row.problem ?: continue
                val word = row.word?.let { VoiceText.word(it) } ?: stringResource(R.string.voice_choose_word)
                Fine(stringResource(R.string.voice_summary_problem, word, VoiceText.problem(problem)), color = Manent.colors.warning)
            }
        }
        error?.let { Fine(VoiceText.error(it), color = Manent.colors.danger) }
        SecondaryButton(stringResource(R.string.home_voice_settings), onSettings)
    }
}

/** "“ok home” → Home Assistant · “hey phone” → This phone", or a note that no word is set. */
@Composable
private fun summary(card: VoiceCard): String {
    if (card.rows.isEmpty()) return stringResource(R.string.voice_summary_none)
    val parts = card.rows.map { row ->
        val word = row.word?.let { VoiceText.word(it) } ?: stringResource(R.string.voice_choose_word)
        stringResource(R.string.voice_summary_route, word, VoiceText.destination(choiceOf(row.route.destination)))
    }
    return parts.joinToString(stringResource(R.string.voice_summary_separator))
}
