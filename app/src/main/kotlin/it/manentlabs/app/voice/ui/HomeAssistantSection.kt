// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
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
import it.manentlabs.app.ui.theme.Manent
import it.manentlabs.app.ui.theme.SecondaryButton
import it.manentlabs.app.ui.theme.Section
import it.manentlabs.app.ui.theme.StatusLine
import it.manentlabs.app.ui.theme.StatusTone
import it.manentlabs.app.voice.homeassistant.ConnectionCheck
import it.manentlabs.app.voice.homeassistant.Pipeline
import it.manentlabs.app.voice.routes.VoiceConfig

/** Home Assistant's address and token, the connection test, and which assistant runs the commands. */
@Composable
internal fun HomeAssistantSection(
    number: Int,
    config: VoiceConfig,
    test: HomeAssistantTest,
    onSaveAndTest: (address: String, token: String) -> Unit,
    onPipeline: (String?) -> Unit,
) {
    var address by rememberSaveable(config.homeAssistantUrl) { mutableStateOf(config.homeAssistantUrl) }
    var token by rememberSaveable { mutableStateOf("") }
    val testing = test == HomeAssistantTest.Running

    Section(
        number = number,
        label = stringResource(R.string.voice_home_assistant),
        title = stringResource(R.string.voice_home_assistant_title),
        lede = stringResource(R.string.voice_home_assistant_hint),
    ) {
        Field(
            value = address,
            onValueChange = { address = it },
            label = stringResource(R.string.voice_home_assistant_address),
            placeholder = stringResource(R.string.voice_home_assistant_placeholder),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Field(
            value = token,
            onValueChange = { token = it },
            label = stringResource(if (config.hasToken) R.string.voice_home_assistant_token_saved else R.string.voice_home_assistant_token),
            placeholder = stringResource(R.string.voice_home_assistant_token_placeholder),
            secret = true,
        )
        SecondaryButton(
            stringResource(R.string.voice_save_and_test),
            onClick = {
                onSaveAndTest(address, token)
                token = ""
            },
            enabled = address.isNotBlank() && (token.isNotBlank() || config.hasToken) && !testing,
            leading = if (testing) {
                { CircularProgressIndicator(Modifier.size(16.dp), color = Manent.colors.muted, strokeWidth = 2.dp) }
            } else {
                null
            },
        )
        if (test is HomeAssistantTest.Done) {
            val check = test.check
            StatusLine(VoiceText.check(check), if (check is ConnectionCheck.Ok) StatusTone.Good else StatusTone.Bad)
            if (check is ConnectionCheck.Ok && check.pipelines.isNotEmpty()) PipelinePicker(check, config.pipeline, onPipeline)
        }
    }
}

@Composable
private fun PipelinePicker(check: ConnectionCheck.Ok, chosen: String?, onPipeline: (String?) -> Unit) {
    val preferredName = check.pipelines.firstOrNull { it.id == check.preferred }?.name
    val preferredLabel = preferredName?.let { stringResource(R.string.voice_pipeline_preferred_named, it) }
        ?: stringResource(R.string.voice_pipeline_preferred)
    val options: List<Pipeline?> = listOf(null) + check.pipelines
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.voice_pipeline_label), style = MaterialTheme.typography.titleSmall, color = Manent.colors.ink)
        Spacer(Modifier.width(12.dp))
        Dropdown(
            current = check.pipelines.firstOrNull { it.id == chosen }?.name ?: preferredLabel,
            options = options,
            optionLabel = { pipeline ->
                when {
                    pipeline == null -> preferredLabel
                    pipeline.language == null -> pipeline.name
                    else -> stringResource(R.string.voice_pipeline_option, pipeline.name, pipeline.language)
                }
            },
            onPick = { onPipeline(it?.id) },
            modifier = Modifier.weight(1f),
        )
    }
}
