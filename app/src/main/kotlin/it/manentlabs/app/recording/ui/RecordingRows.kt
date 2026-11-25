// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.manentlabs.app.R
import it.manentlabs.app.recording.LocalRecording
import it.manentlabs.app.recording.LocalStatus
import it.manentlabs.app.recording.RecordingDates
import it.manentlabs.app.ui.theme.Manent
import it.manentlabs.app.ui.theme.StatusLine
import it.manentlabs.app.ui.theme.StatusTone
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** A recording waiting on this phone: its title, date, where it stands, and a delete button. */
@Composable
fun LocalRecordingRow(recording: LocalRecording, onDelete: () -> Unit) {
    val status = LocalStatus.of(recording)
    val (statusText, tone) = when (status) {
        LocalStatus.Waiting -> stringResource(R.string.recording_status_waiting) to StatusTone.Idle
        is LocalStatus.Unsent -> pluralStringResource(R.plurals.recording_status_uploading, status.parts, status.parts) to StatusTone.Live
        is LocalStatus.Refused -> stringResource(R.string.recording_status_refused, status.reason) to StatusTone.Bad
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                recording.session.title ?: stringResource(R.string.recording_untitled),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                color = Manent.colors.ink,
            )
            Text(
                dateOf(Instant.ofEpochMilli(recording.session.startedAtMs)),
                style = MaterialTheme.typography.bodySmall,
                color = Manent.colors.muted,
            )
            StatusLine(statusText, tone)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.recording_delete), tint = Manent.colors.muted)
        }
    }
}

private fun dateOf(instant: Instant): String = RecordingDates.format(instant, ZoneId.systemDefault(), Locale.getDefault())
