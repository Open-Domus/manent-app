// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.manentlabs.app.R
import it.manentlabs.app.ui.theme.Fine
import it.manentlabs.app.ui.theme.Manent
import it.manentlabs.app.ui.theme.PrimaryButton
import it.manentlabs.app.ui.theme.SecondaryButton
import it.manentlabs.app.ui.theme.Section
import it.manentlabs.app.ui.theme.StatusLine
import it.manentlabs.app.ui.theme.StatusTone
import it.manentlabs.app.voice.wakeword.WakeWord

/** The wake-word models: what is missing and where it comes from, downloading, and importing one's own. */
@Composable
internal fun ModelsSection(
    number: Int,
    missing: List<WakeWord>,
    downloading: Boolean,
    onDownload: () -> Unit,
    onImport: (Uri) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onImport) }
    Section(
        number = number,
        label = stringResource(R.string.voice_section_models),
        title = stringResource(R.string.voice_models_title),
    ) {
        if (missing.isEmpty()) {
            StatusLine(stringResource(R.string.voice_models_ready), StatusTone.Good)
        } else {
            Text(stringResource(R.string.voice_models_explained), style = MaterialTheme.typography.bodyMedium, color = Manent.colors.ink)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (word in missing) {
                    Fine(stringResource(R.string.voice_model_origin, word.phrase, VoiceText.origin(word.origin)), color = Manent.colors.ink)
                }
            }
            PrimaryButton(
                stringResource(if (downloading) R.string.voice_downloading else R.string.voice_download_models),
                onClick = onDownload,
                enabled = !downloading,
                leading = if (downloading) {
                    { CircularProgressIndicator(Modifier.size(16.dp), color = Manent.colors.muted, strokeWidth = 2.dp) }
                } else {
                    null
                },
            )
        }
        Fine(stringResource(R.string.voice_models_custom_hint))
        SecondaryButton(stringResource(R.string.voice_import_model), onClick = { picker.launch(arrayOf("application/octet-stream", "*/*")) })
    }
}
