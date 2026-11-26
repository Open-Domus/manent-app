// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.manentlabs.app.R
import it.manentlabs.app.speech.ui.SpokenAnswersSection
import it.manentlabs.app.ui.theme.Fine
import it.manentlabs.app.ui.theme.Hairline
import it.manentlabs.app.ui.theme.Manent
import it.manentlabs.app.ui.theme.ManentPage
import it.manentlabs.app.ui.theme.ManentText
import it.manentlabs.app.ui.theme.SecondaryButton
import it.manentlabs.app.ui.theme.Section
import it.manentlabs.app.ui.theme.TextAction
import it.manentlabs.app.ui.theme.ToggleRow
import it.manentlabs.app.voice.phone.PhonePermissions
import it.manentlabs.app.voice.routes.Destination

/**
 * Everything about voice commands: wake words and where they go, the models,
 * Home Assistant, Hermes Agent and their spoken replies, phone actions and transcription.
 */
@Composable
fun VoiceCommandsScreen(onBack: () -> Unit, viewModel: VoiceCommandsViewModel = hiltViewModel()) {
    val card by viewModel.card.collectAsStateWithLifecycle()
    val downloading by viewModel.downloading.collectAsStateWithLifecycle()
    val test by viewModel.test.collectAsStateWithLifecycle()
    val hermesTest by viewModel.hermesTest.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val calling = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.onResume() }

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    ManentPage(title = stringResource(R.string.voice_title), onBack = onBack) {
        val c = card
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(stringResource(R.string.voice_intro), style = ManentText.lede, color = Manent.colors.ink)
            if (c != null) {
                Column(Modifier.fillMaxWidth()) {
                    Hairline()
                    ListenToggle(c, viewModel::setEnabled)
                    Hairline()
                }
            }
            error?.let { Fine(VoiceText.error(it), color = Manent.colors.danger) }
        }
        if (c == null) return@ManentPage

        var number = 0
        val openOverlay = { PhonePermissions.openOverlaySettings(context) }
        Section(++number, stringResource(R.string.voice_section_wake_words), stringResource(R.string.voice_words_title)) {
            Column {
                Hairline()
                for (row in c.rows) {
                    WakeWordRow(
                        row = row,
                        overlayAllowed = c.overlayAllowed,
                        onWord = { viewModel.setWord(row.route.id, it) },
                        onDestination = { choice ->
                            viewModel.setDestination(row.route.id, choice)
                            if (choice == DestinationChoice.PHONE && !c.overlayAllowed) openOverlay()
                        },
                        onRemove = { viewModel.removeRoute(row.route.id) },
                        onSaveWebhook = { url, secret -> viewModel.saveWebhook(row.route.id, url, secret) },
                        onAllowOverlay = openOverlay,
                    )
                    Hairline()
                }
            }
            if (c.unusedWords.isNotEmpty()) {
                SecondaryButton(
                    stringResource(R.string.voice_add_wake_word),
                    onClick = viewModel::addRoute,
                    leading = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }

        ModelsSection(++number, c.missing, downloading, viewModel::downloadModels) { viewModel.importModel(context.contentResolver, it) }

        if (c.config.routes.any { it.destination == Destination.HomeAssistant }) {
            HomeAssistantSection(++number, c.config, test, viewModel::saveHomeAssistant, viewModel::setPipeline)
        }
        if (c.config.routes.any { it.destination == Destination.Hermes }) {
            HermesSection(++number, c.config, hermesTest, viewModel::saveHermes)
        }
        SpokenAnswersSection(++number)
        c.phoneRows?.let { rows ->
            PhoneActionsSection(++number, rows, c.callingAllowed, viewModel::setPhoneApp) { calling.launch(PhonePermissions.CALLING) }
        }
        c.transcription?.let { TranscriptionSection(++number, it) }

        if (c.showForget) {
            Column(Modifier.fillMaxWidth()) {
                Hairline()
                TextAction(stringResource(R.string.voice_forget), viewModel::forget, danger = true)
            }
        }
    }
}

/** The switch that starts listening for wake words; off until at least one is ready. */
@Composable
internal fun ListenToggle(card: VoiceCard, onChange: (Boolean) -> Unit) {
    ToggleRow(
        title = stringResource(R.string.voice_listen),
        hint = if (card.anyActive) null else stringResource(R.string.voice_listen_needs_setup),
        checked = card.config.enabled && card.anyActive,
        onCheckedChange = onChange,
        enabled = card.anyActive,
    )
}

@Composable
private fun TranscriptionSection(number: Int, line: TranscriptionLine) {
    Section(
        number = number,
        label = stringResource(R.string.voice_section_transcription),
        title = stringResource(R.string.voice_transcription_title),
        lede = stringResource(R.string.voice_transcription_hint),
    ) {
        when (line) {
            is TranscriptionLine.Unavailable -> Fine(
                stringResource(R.string.voice_transcription_unavailable, VoiceText.block(line.block)),
                color = Manent.colors.warning,
            )
            is TranscriptionLine.Timing -> {
                val elapsed = VoiceText.seconds(line.timing.elapsedMs)
                val audio = VoiceText.seconds(line.timing.audioMs)
                Fine(stringResource(if (line.timing.heard) R.string.voice_timing else R.string.voice_timing_nothing_heard, elapsed, audio))
            }
            TranscriptionLine.Ready -> Unit
        }
    }
}
