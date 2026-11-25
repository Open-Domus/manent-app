// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable.omi.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.manentlabs.app.R
import it.manentlabs.app.ui.theme.Fine
import it.manentlabs.app.ui.theme.Hairline
import it.manentlabs.app.ui.theme.Manent
import it.manentlabs.app.ui.theme.PrimaryButton
import it.manentlabs.app.ui.theme.SecondaryButton
import it.manentlabs.app.ui.theme.Section
import it.manentlabs.app.ui.theme.TextAction
import it.manentlabs.app.ui.theme.ToggleRow
import it.manentlabs.app.wearable.omi.OmiState

/** The pendant on Home: linking it, its status, "Capture all day", details and forgetting it. */
@Composable
fun OmiSection(number: Int, modifier: Modifier = Modifier, viewModel: OmiViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val details by viewModel.details.collectAsStateWithLifecycle()
    val captureAllDay by viewModel.captureAllDay.collectAsStateWithLifecycle()
    val associating by viewModel.associating.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val pendingChooser by viewModel.chooser.collectAsStateWithLifecycle()
    var detailsOpen by rememberSaveable { mutableStateOf(false) }

    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewModel.onChooserResult(it.resultCode, it.data)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.associate() else viewModel.onPermissionDenied()
    }
    LaunchedEffect(pendingChooser) {
        val sender = pendingChooser ?: return@LaunchedEffect
        viewModel.onChooserLaunched()
        try {
            chooser.launch(IntentSenderRequest.Builder(sender).build())
        } catch (_: ActivityNotFoundException) {
            viewModel.onChooserResult(0, null)
        }
    }

    val notPaired = state == OmiState.NotPaired
    Section(
        number = number,
        label = stringResource(R.string.home_pendant_label),
        title = stringResource(R.string.home_pendant_title),
        lede = if (notPaired) stringResource(R.string.omi_intro) else null,
        modifier = modifier,
    ) {
        if (notPaired) {
            Fine(stringResource(R.string.omi_intro_disconnect_app))
            PrimaryButton(
                stringResource(if (associating) R.string.omi_looking else R.string.omi_link),
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    } else {
                        viewModel.associate()
                    }
                },
                enabled = !associating,
            )
        } else {
            Paired(
                state = state,
                captureAllDay = captureAllDay,
                onCaptureAllDay = viewModel::setCaptureAllDay,
                onDetails = {
                    viewModel.refreshDetails()
                    detailsOpen = true
                },
                onForget = viewModel::forget,
            )
        }
        error?.let {
            Fine(
                when (it) {
                    is CardMessage.Text -> it.text.resolve()
                    is CardMessage.System -> it.text
                },
                color = Manent.colors.danger,
            )
        }
    }

    if (detailsOpen) {
        OmiDetailsSheet(state, details, onFind = viewModel::find, onDismiss = { detailsOpen = false })
    }
}

@Composable
private fun Paired(
    state: OmiState,
    captureAllDay: Boolean,
    onCaptureAllDay: (Boolean) -> Unit,
    onDetails: () -> Unit,
    onForget: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OmiStatusRow(state)
        OmiText.detail(state, captureAllDay)?.let {
            Fine(it.resolve(), color = if (state is OmiState.Unsupported) Manent.colors.danger else Manent.colors.muted)
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Hairline()
        ToggleRow(
            title = stringResource(R.string.omi_capture_all_day),
            hint = stringResource(R.string.omi_capture_all_day_description),
            checked = captureAllDay,
            onCheckedChange = onCaptureAllDay,
        )
        Hairline()
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SecondaryButton(stringResource(R.string.omi_details), onDetails)
        Spacer(Modifier.width(20.dp))
        TextAction(stringResource(R.string.omi_forget), onForget, danger = true)
    }
}
