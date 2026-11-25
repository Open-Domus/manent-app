// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.manentlabs.app.R
import it.manentlabs.app.recording.ElapsedFormat
import it.manentlabs.app.recording.RecordingGate
import it.manentlabs.app.recording.RecordingStatus
import it.manentlabs.app.ui.theme.ChoiceChip
import it.manentlabs.app.ui.theme.Field
import it.manentlabs.app.ui.theme.Fine
import it.manentlabs.app.ui.theme.Hairline
import it.manentlabs.app.ui.theme.Manent
import it.manentlabs.app.ui.theme.ManentText
import it.manentlabs.app.ui.theme.PrimaryButton
import it.manentlabs.app.ui.theme.SecondaryButton
import it.manentlabs.app.ui.theme.Section
import it.manentlabs.app.ui.theme.StatusLine
import it.manentlabs.app.ui.theme.StatusTone
import it.manentlabs.app.ui.theme.ToggleRow
import it.manentlabs.app.wearable.RecordingInput
import kotlinx.coroutines.delay

/** The recorded time of [status], ticking once a second. */
@Composable
internal fun rememberElapsed(status: RecordingStatus.Active): Long {
    val elapsed by produceState(status.elapsedMs(SystemClock.elapsedRealtime()), status) {
        while (true) {
            value = status.elapsedMs(SystemClock.elapsedRealtime())
            delay(1_000)
        }
    }
    return elapsed
}

/** What the running recording is doing, in a few words. */
@Composable
internal fun recorderHeadline(status: RecordingStatus.Active, wearableName: String): String {
    val device = wearableName.ifBlank { stringResource(R.string.recording_wearable_fallback) }
    return when {
        status.paused -> stringResource(R.string.recording_paused)
        status.ambient -> stringResource(R.string.recording_ambient_with, device)
        status.input == RecordingInput.WEARABLE -> stringResource(R.string.recording_with, device)
        else -> stringResource(R.string.recording_in_progress)
    }
}

/** The recording in progress: its state, a running clock and its controls. */
@Composable
fun RecorderSection(
    number: Int,
    status: RecordingStatus.Active,
    wearableName: String,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    val elapsed = rememberElapsed(status)
    val device = wearableName.ifBlank { stringResource(R.string.recording_wearable_fallback) }
    val wearable = status.input == RecordingInput.WEARABLE
    Section(number, stringResource(R.string.recording_now_label), status.title ?: stringResource(R.string.recording_untitled)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusLine(recorderHeadline(status, wearableName), if (status.paused) StatusTone.Idle else StatusTone.Live)
            Text(ElapsedFormat.format(elapsed), style = ManentText.figure, color = Manent.colors.ink, maxLines = 1, overflow = TextOverflow.Clip)
            Fine(pluralStringResource(R.plurals.recording_parts_saved, status.partsFiled, status.partsFiled))
        }
        if (status.inputLost) {
            Fine(
                if (wearable) stringResource(R.string.recording_wearable_lost, device) else stringResource(R.string.recording_mic_busy),
                color = Manent.colors.danger,
            )
        }
        if (status.ambient) Fine(stringResource(R.string.recording_ambient_hint, device))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(
                stringResource(if (status.paused) R.string.recording_resume else R.string.recording_pause),
                onClick = if (status.paused) onResume else onPause,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                stringResource(if (status.ambient) R.string.recording_turn_off else R.string.recording_stop),
                onClick = onStop,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** How to start a recording: the input, a title, the upload rule and the Start button. */
@Composable
fun StartSection(
    number: Int,
    input: RecordingInput,
    wearableName: String,
    wearableReady: Boolean,
    title: String,
    onTitle: (String) -> Unit,
    onPickInput: (RecordingInput) -> Unit,
    wifiOnly: Boolean,
    onWifiOnly: (Boolean) -> Unit,
    micDenied: Boolean,
    starting: Boolean,
    onStart: () -> Unit,
) {
    val device = wearableName.ifBlank { stringResource(R.string.recording_wearable_fallback) }
    Section(
        number = number,
        label = stringResource(R.string.recording_new_label),
        title = stringResource(R.string.recording_start_title),
        lede = if (input == RecordingInput.WEARABLE) {
            stringResource(R.string.recording_start_body_wearable, device)
        } else {
            stringResource(R.string.recording_start_body_phone)
        },
    ) {
        if (wearableReady) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(stringResource(R.string.recording_input_phone), input == RecordingInput.PHONE, { onPickInput(RecordingInput.PHONE) })
                    ChoiceChip(device, input == RecordingInput.WEARABLE, { onPickInput(RecordingInput.WEARABLE) })
                }
                Fine(stringResource(R.string.recording_input_hint, device))
            }
        }
        Field(
            value = title,
            onValueChange = onTitle,
            label = stringResource(R.string.recording_title_field),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        Column(Modifier.fillMaxWidth()) {
            Hairline()
            ToggleRow(
                title = stringResource(R.string.recording_wifi_only),
                hint = stringResource(R.string.recording_wifi_only_hint),
                checked = wifiOnly,
                onCheckedChange = onWifiOnly,
            )
            Hairline()
        }
        if (micDenied) Fine(stringResource(R.string.recording_mic_denied), color = Manent.colors.danger)
        PrimaryButton(
            stringResource(if (starting) R.string.recording_starting else R.string.recording_start),
            onClick = onStart,
            enabled = !starting,
            modifier = Modifier.fillMaxWidth(),
            leading = { Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        Fine(stringResource(R.string.recording_consent_hint))
    }
}

/** Shown instead of the Start section when no gateway takes recordings now; offers to connect one when none is. */
@Composable
fun UnavailableSection(number: Int, gate: RecordingGate, onConnectGateway: () -> Unit) {
    Section(
        number = number,
        label = stringResource(R.string.recording_new_label),
        title = stringResource(R.string.recording_unavailable_title),
        lede = stringResource(unavailableReason(gate)),
    ) {
        if (gate == RecordingGate.NOT_PAIRED) PrimaryButton(stringResource(R.string.app_pair_title), onConnectGateway)
    }
}
