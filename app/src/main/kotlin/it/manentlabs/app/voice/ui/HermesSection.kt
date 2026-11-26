// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.manentlabs.app.R
import it.manentlabs.app.ui.theme.Field
import it.manentlabs.app.ui.theme.Fine
import it.manentlabs.app.ui.theme.Manent
import it.manentlabs.app.ui.theme.SecondaryButton
import it.manentlabs.app.ui.theme.Section
import it.manentlabs.app.ui.theme.StatusLine
import it.manentlabs.app.ui.theme.StatusTone
import it.manentlabs.app.voice.hermes.HermesCheck
import it.manentlabs.app.voice.routes.VoiceConfig

/** Hermes Agent's API server: its address, its key and the connection test. */
@Composable
internal fun HermesSection(
    number: Int,
    config: VoiceConfig,
    test: HermesTest,
    onSaveAndTest: (address: String, key: String) -> Unit,
) {
    var address by rememberSaveable(config.hermesUrl) { mutableStateOf(config.hermesUrl) }
    var key by rememberSaveable { mutableStateOf("") }
    val testing = test == HermesTest.Running

    Section(
        number = number,
        label = stringResource(R.string.voice_hermes),
        title = stringResource(R.string.voice_hermes_title),
        lede = stringResource(R.string.voice_hermes_hint),
    ) {
        Field(
            value = address,
            onValueChange = { address = it },
            label = stringResource(R.string.voice_hermes_address),
            placeholder = stringResource(R.string.voice_hermes_placeholder),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Field(
            value = key,
            onValueChange = { key = it },
            label = stringResource(if (config.hasHermesKey) R.string.voice_hermes_key_saved else R.string.voice_hermes_key),
            placeholder = stringResource(R.string.voice_hermes_key_placeholder),
            secret = true,
        )
        Fine(stringResource(R.string.voice_hermes_setup))
        SecondaryButton(
            stringResource(R.string.voice_save_and_test),
            onClick = {
                onSaveAndTest(address, key)
                key = ""
            },
            enabled = address.isNotBlank() && (key.isNotBlank() || config.hasHermesKey) && !testing,
            leading = if (testing) {
                { CircularProgressIndicator(Modifier.size(16.dp), color = Manent.colors.muted, strokeWidth = 2.dp) }
            } else {
                null
            },
        )
        if (test is HermesTest.Done) {
            StatusLine(VoiceText.hermesCheck(test.check), if (test.check is HermesCheck.Ok) StatusTone.Good else StatusTone.Bad)
        }
    }
}
