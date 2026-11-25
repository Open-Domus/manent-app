// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.manentlabs.app.R
import it.manentlabs.app.ui.theme.Dropdown
import it.manentlabs.app.ui.theme.Field
import it.manentlabs.app.ui.theme.Fine
import it.manentlabs.app.ui.theme.Manent
import it.manentlabs.app.ui.theme.SecondaryButton
import it.manentlabs.app.voice.routes.Destination

/** One wake word: which word, where it goes, the webhook's address, and what still blocks it. */
@Composable
internal fun WakeWordRow(
    row: RouteRow,
    overlayAllowed: Boolean,
    onWord: (String) -> Unit,
    onDestination: (DestinationChoice) -> Unit,
    onRemove: () -> Unit,
    onSaveWebhook: (url: String, secret: String) -> Boolean,
    onAllowOverlay: () -> Unit,
) {
    Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dropdown(
                current = row.word?.let { VoiceText.word(it) } ?: stringResource(R.string.voice_choose_word),
                options = row.choices,
                optionLabel = { VoiceText.word(it) },
                onPick = { onWord(it.id) },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.voice_remove_wake_word), tint = Manent.colors.muted)
            }
        }
        Row(Modifier.padding(end = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.voice_route_arrow),
                Modifier.width(28.dp),
                style = MaterialTheme.typography.titleMedium,
                color = Manent.colors.accent,
            )
            Dropdown(
                current = VoiceText.destination(choiceOf(row.route.destination)),
                options = DestinationChoice.entries,
                optionLabel = { VoiceText.destination(it) },
                onPick = onDestination,
                modifier = Modifier.weight(1f),
            )
        }
        when (val destination = row.route.destination) {
            is Destination.Webhook -> WebhookFields(row.route.id, destination.url, row.secretSaved, onSaveWebhook)
            Destination.Phone -> Fine(stringResource(R.string.voice_phone_hint))
            Destination.HomeAssistant -> Unit
        }
        row.problem?.let { Fine(VoiceText.problem(it), color = Manent.colors.warning) }
        if (row.route.destination == Destination.Phone && !overlayAllowed) {
            Fine(stringResource(R.string.voice_overlay_needed), color = Manent.colors.warning)
            SecondaryButton(stringResource(R.string.voice_allow), onAllowOverlay)
        }
    }
}

@Composable
private fun WebhookFields(routeId: String, savedUrl: String, secretSaved: Boolean, onSave: (String, String) -> Boolean) {
    var url by rememberSaveable(routeId, savedUrl) { mutableStateOf(savedUrl) }
    var secret by rememberSaveable(routeId) { mutableStateOf("") }
    Field(
        value = url,
        onValueChange = { url = it },
        label = stringResource(R.string.voice_webhook_address),
        placeholder = stringResource(R.string.voice_webhook_placeholder),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
    )
    Field(
        value = secret,
        onValueChange = { secret = it },
        label = stringResource(if (secretSaved) R.string.voice_webhook_secret_saved else R.string.voice_webhook_secret),
        secret = true,
        hint = stringResource(R.string.voice_webhook_hint),
    )
    SecondaryButton(
        stringResource(R.string.voice_save),
        onClick = { if (onSave(url, secret)) secret = "" },
        enabled = url.isNotBlank() && (url.trim() != savedUrl || secret.isNotBlank()),
    )
}

internal fun choiceOf(destination: Destination) = when (destination) {
    Destination.HomeAssistant -> DestinationChoice.HOME_ASSISTANT
    is Destination.Webhook -> DestinationChoice.WEBHOOK
    Destination.Phone -> DestinationChoice.PHONE
}
