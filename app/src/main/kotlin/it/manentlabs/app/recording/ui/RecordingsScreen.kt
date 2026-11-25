// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.manentlabs.app.R
import it.manentlabs.app.recording.RecordingGate
import it.manentlabs.app.recording.RecordingStatus
import it.manentlabs.app.ui.theme.Fine
import it.manentlabs.app.ui.theme.Hairline
import it.manentlabs.app.ui.theme.Manent
import it.manentlabs.app.ui.theme.ManentPage
import it.manentlabs.app.ui.theme.Section
import it.manentlabs.app.ui.theme.TextAction
import it.manentlabs.app.wearable.RecordingInput
import it.manentlabs.app.wearable.omi.ui.OmiRow

/**
 * Recordings: the one in progress, a way to start one, the pendant's link,
 * and the recordings still waiting to reach the gateway.
 */
@Composable
fun RecordingsScreen(
    onBack: () -> Unit,
    onManageWearable: () -> Unit,
    onConnectGateway: () -> Unit,
    viewModel: RecordingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val gate by viewModel.gate.collectAsStateWithLifecycle()
    val local by viewModel.local.collectAsStateWithLifecycle()
    val deleteError by viewModel.deleteError.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val wearableReady by viewModel.wearableReady.collectAsStateWithLifecycle()
    val wifiOnly by viewModel.wifiOnly.collectAsStateWithLifecycle()
    val starting by viewModel.starting.collectAsStateWithLifecycle()
    var title by rememberSaveable { mutableStateOf("") }
    var micDenied by rememberSaveable { mutableStateOf(false) }

    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val startNow = {
        viewModel.start(title, input)
        title = ""
    }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micDenied = !granted
        if (granted) {
            startNow()
            if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val onStart = {
        val needsMic = input == RecordingInput.PHONE &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        if (needsMic) microphone.launch(Manifest.permission.RECORD_AUDIO) else startNow()
    }

    val active = status as? RecordingStatus.Active
    val available = gate == RecordingGate.OPEN

    ManentPage(title = stringResource(R.string.recording_screen_title), onBack = onBack) {
        var number = 0
        if (active != null) {
            RecorderSection(++number, active, viewModel.wearableName, viewModel::pause, viewModel::resume, viewModel::stop)
        }
        if (active == null || active.ambient) {
            if (available) {
                StartSection(
                    number = ++number,
                    input = input,
                    wearableName = viewModel.wearableName,
                    wearableReady = wearableReady,
                    title = title,
                    onTitle = { title = it.take(TITLE_MAX) },
                    onPickInput = viewModel::pickInput,
                    wifiOnly = wifiOnly,
                    onWifiOnly = viewModel::setWifiOnly,
                    micDenied = micDenied,
                    starting = starting,
                    onStart = onStart,
                )
            } else {
                UnavailableSection(++number, gate, onConnectGateway)
            }
        }
        if (available) OmiRow(onManage = onManageWearable)
        if (local.isNotEmpty() || deleteError != null) {
            Section(++number, stringResource(R.string.recording_queue_label), stringResource(R.string.recording_queue_title)) {
                deleteError?.let { error ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Fine(stringResource(error), Modifier.weight(1f), color = Manent.colors.danger)
                        Spacer(Modifier.width(8.dp))
                        TextAction(stringResource(R.string.recording_dismiss), viewModel::dismissDeleteError)
                    }
                }
                Column {
                    local.forEachIndexed { i, recording ->
                        key(recording.session.id) {
                            if (i > 0) Hairline()
                            LocalRecordingRow(recording, onDelete = { viewModel.delete(recording) })
                        }
                    }
                }
            }
        }
    }
}

private const val TITLE_MAX = 200
