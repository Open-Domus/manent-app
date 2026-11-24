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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                title = { Text("Connect Omnesis") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "On the gateway host run:\n" +
                    "omnesis devices pair --kind android " +
                    "--scopes read,write:omnesis-recordings,write:omnesis-notes",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = payload,
                onValueChange = { payload = it },
                label = { Text("QR payload (JSON)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            Button(onClick = { pair { sessions.pairFromPayload(payload) } }, enabled = !busy && payload.isNotBlank()) {
                Text("Pair from payload")
            }
            Text("Or by hand", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(url, { url = it }, label = { Text("Gateway URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(code, { code = it }, label = { Text("Pairing code") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                fingerprint,
                { fingerprint = it },
                label = { Text("Certificate fingerprint (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(
                onClick = { pair { sessions.pairManually(url, code, fingerprint) } },
                enabled = !busy && url.isNotBlank() && code.isNotBlank(),
            ) { Text("Pair") }
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
        topBar = { TopAppBar(title = { Text("Manent") }) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OmiCard()
            VoiceCommandsCard()
            SpokenAnswersCard(spokenAnswers, speaker)
            Button(onClick = onOpenRecordings, modifier = Modifier.fillMaxWidth()) { Text("Recordings") }
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
            Text("Spoken answers", style = MaterialTheme.typography.titleMedium)
            Text(
                "The phone reads Home Assistant's reply aloud, through headphones when they are connected.",
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
                            Text(spokenAnswersLabel(option), style = MaterialTheme.typography.bodyMedium)
                            Text(spokenAnswersHint(option), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            OutlinedButton(onClick = { speaker.speak(sampleAnswer()) }, enabled = mode != SpokenAnswers.OFF) {
                Text("Try it")
            }
        }
    }
}

private fun spokenAnswersLabel(mode: SpokenAnswers): String = when (mode) {
    SpokenAnswers.OFF -> "Off"
    SpokenAnswers.QUESTIONS -> "Answers to questions"
    SpokenAnswers.ALWAYS -> "Every reply"
}

private fun spokenAnswersHint(mode: SpokenAnswers): String = when (mode) {
    SpokenAnswers.OFF -> "The pendant's pulses only"
    SpokenAnswers.QUESTIONS -> "“What temperature is the boiler?”"
    SpokenAnswers.ALWAYS -> "Confirmations too: “Turned on the light”"
}

/** A reply like Home Assistant's, in the phone's language, which the voice speaks in. */
private fun sampleAnswer(): String =
    if (java.util.Locale.getDefault().language == "it") "La caldaia è a 52 gradi." else "The boiler is at 52 degrees."

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
                    "Not connected. Recordings stay on this phone until a gateway is connected.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = onConnect) { Text("Connect") }
            } else {
                Text(paired.gatewayName ?: paired.url, style = MaterialTheme.typography.bodyMedium)
                Text(statusLine(status), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { scope.launch { sessions.refreshStatus() } }) { Text("Check") }
                    TextButton(onClick = sessions::unpair) { Text("Disconnect") }
                }
            }
        }
    }
}

internal fun statusLine(status: GatewayStatus?): String = when {
    status == null -> "Checking…"
    !status.reachable -> "Not reachable: ${status.error}"
    !status.experimental -> "Experimental mode is off on the gateway (OMNESIS_EXPERIMENTAL=1)"
    !status.recordings -> "The gateway does not take recordings"
    status.dictationEnabled == false -> "Gateway transcription is off"
    else -> "Takes recordings"
}
