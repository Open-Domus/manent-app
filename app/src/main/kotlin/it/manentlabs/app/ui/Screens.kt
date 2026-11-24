// SPDX-License-Identifier: MIT

package it.manentlabs.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.manentlabs.app.R
import it.manentlabs.app.omnesis.GatewaySessions
import it.manentlabs.app.omnesis.GatewayStatus
import it.manentlabs.app.speech.AnswerSpeaker
import it.manentlabs.app.speech.SpokenAnswerSettings
import it.manentlabs.app.speech.SpokenAnswers
import androidx.compose.ui.semantics.Role
import dev.omnesis.android.designsystem.components.OmnesisCard
import dev.omnesis.android.designsystem.theme.OmTheme
import dev.omnesis.android.feature.assist.ui.VoiceCommandsCard
import dev.omnesis.android.feature.omi.ui.OmiCard
import kotlinx.coroutines.launch

/** Connect an Omnesis gateway, with this phone as a device of its own. Optional: Manent works without one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(sessions: GatewaySessions, onPaired: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var payload by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var fingerprint by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun pair(block: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try {
                block()
                onPaired()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = e.message ?: e::class.simpleName
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        containerColor = OmTheme.colors.bgPrimary,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_pair_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.app_back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.app_pair_instructions) + "\n" + PAIR_COMMAND,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = payload,
                onValueChange = { payload = it },
                label = { Text(stringResource(R.string.app_pair_payload_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            Button(onClick = { pair { sessions.pairFromPayload(payload) } }, enabled = !busy && payload.isNotBlank()) {
                Text(stringResource(R.string.app_pair_from_payload))
            }
            Text(stringResource(R.string.app_pair_by_hand), style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.app_pair_gateway_url)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(code, { code = it }, label = { Text(stringResource(R.string.app_pair_code)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                fingerprint,
                { fingerprint = it },
                label = { Text(stringResource(R.string.app_pair_fingerprint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(
                onClick = { pair { sessions.pairManually(url, code, fingerprint) } },
                enabled = !busy && url.isNotBlank() && code.isNotBlank(),
            ) { Text(stringResource(R.string.app_pair)) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** The wearable, voice commands and connected services on one page; recordings one tap away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    sessions: GatewaySessions,
    spokenAnswers: SpokenAnswerSettings,
    speaker: AnswerSpeaker,
    onOpenRecordings: () -> Unit,
    onConnectOmnesis: () -> Unit,
) {
    Scaffold(
        containerColor = OmTheme.colors.bgPrimary,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OmiCard()
            VoiceCommandsCard()
            SpokenAnswersCard(spokenAnswers, speaker)
            Button(onClick = onOpenRecordings, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.app_recordings)) }
            OmnesisConnection(sessions, onConnectOmnesis)
        }
    }
}

/** Whether the phone reads Home Assistant's replies aloud. */
@Composable
private fun SpokenAnswersCard(settings: SpokenAnswerSettings, speaker: AnswerSpeaker) {
    val mode by settings.state.collectAsStateWithLifecycle()
    OmnesisCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.app_spoken_answers_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.app_spoken_answers_description),
                style = MaterialTheme.typography.bodySmall,
            )
            Column(Modifier.selectableGroup()) {
                SpokenAnswers.entries.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = mode == option, role = Role.RadioButton, onClick = { settings.set(option) })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == option, onClick = null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(stringResource(spokenAnswersLabel(option)), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(spokenAnswersHint(option)), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            // A reply like Home Assistant's, in the app's language.
            val sample = stringResource(R.string.app_spoken_answers_sample)
            OutlinedButton(onClick = { speaker.speak(sample) }, enabled = mode != SpokenAnswers.OFF) {
                Text(stringResource(R.string.app_spoken_answers_try))
            }
        }
    }
}

@StringRes
private fun spokenAnswersLabel(mode: SpokenAnswers): Int = when (mode) {
    SpokenAnswers.OFF -> R.string.app_spoken_answers_off
    SpokenAnswers.QUESTIONS -> R.string.app_spoken_answers_questions
    SpokenAnswers.ALWAYS -> R.string.app_spoken_answers_always
}

@StringRes
private fun spokenAnswersHint(mode: SpokenAnswers): Int = when (mode) {
    SpokenAnswers.OFF -> R.string.app_spoken_answers_off_hint
    SpokenAnswers.QUESTIONS -> R.string.app_spoken_answers_questions_hint
    SpokenAnswers.ALWAYS -> R.string.app_spoken_answers_always_hint
}

/** Omnesis is one place recordings can go; until it is connected they stay on the phone. */
@Composable
private fun OmnesisConnection(sessions: GatewaySessions, onConnect: () -> Unit) {
    val scope = rememberCoroutineScope()
    val pairing by sessions.paired.collectAsStateWithLifecycle()
    val status by sessions.status.collectAsStateWithLifecycle()
    LaunchedEffect(pairing) { sessions.refreshStatus() }
    OmnesisCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Omnesis", style = MaterialTheme.typography.titleMedium)
            val paired = pairing
            if (paired == null) {
                Text(
                    stringResource(R.string.app_omnesis_not_connected),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = onConnect) { Text(stringResource(R.string.app_omnesis_connect)) }
            } else {
                Text(paired.gatewayName ?: paired.url, style = MaterialTheme.typography.bodyMedium)
                val line = statusLine(status)
                Text(stringResource(line.text, line.detail.orEmpty()), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { scope.launch { sessions.refreshStatus() } }) { Text(stringResource(R.string.app_omnesis_check)) }
                    TextButton(onClick = sessions::unpair) { Text(stringResource(R.string.app_omnesis_disconnect)) }
                }
            }
        }
    }
}

/** The gateway's state in one line: a string resource and, for [R.string.app_status_unreachable], the error. */
internal data class StatusLine(@StringRes val text: Int, val detail: String? = null)

internal fun statusLine(status: GatewayStatus?): StatusLine = when {
    status == null -> StatusLine(R.string.app_status_checking)
    !status.reachable -> StatusLine(R.string.app_status_unreachable, status.error.orEmpty())
    !status.experimental -> StatusLine(R.string.app_status_experimental_off)
    !status.recordings -> StatusLine(R.string.app_status_no_recordings)
    status.dictationEnabled == false -> StatusLine(R.string.app_status_transcription_off)
    else -> StatusLine(R.string.app_status_takes_recordings)
}

/** The CLI command that issues a pairing code; a command, so never translated. */
private const val PAIR_COMMAND =
    "omnesis devices pair --kind android --scopes read,write:omnesis-recordings,write:omnesis-notes"
