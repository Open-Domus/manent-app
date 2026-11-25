// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Lorenzo Farnararo

package it.manentlabs.app.feature.recording.ui

import android.Manifest
import android.content.res.Resources
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.ui.draw.clip
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import it.manentlabs.app.designsystem.theme.OmRadius
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.manentlabs.app.designsystem.components.OmnesisCard
import it.manentlabs.app.designsystem.theme.OmSpacing
import it.manentlabs.app.designsystem.theme.OmTheme
import it.manentlabs.app.feature.recording.R
import it.manentlabs.app.feature.recording.RecordingInput
import it.manentlabs.app.feature.recording.RecordingState

/** Record an in-person conversation with the phone on the table (experimental). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    onOpenMenu: () -> Unit,
    /** The wearable's status, supplied by the module that links it, shown below the recorder. */
    wearableCard: (@Composable () -> Unit)? = null,
    onOpenSpeakers: (id: String, title: String) -> Unit,
    vm: RecordingViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Back from naming speakers, the counts on the list are fresh.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshGatewayRecent() }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // The notification is how recording is shown and stopped; recording
        // still starts without it, and the system lists the running service.
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.onMicPermission(granted)
        if (granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val untitled = stringResource(R.string.recording_untitled)
    Scaffold(
        containerColor = OmTheme.colors.bgPrimary,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.recording_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenMenu) {
                        Icon(Icons.Outlined.Menu, contentDescription = stringResource(R.string.recording_menu))
                    }
                },
            )
        },
    ) { padding ->
        RecordingContent(
            state = state,
            onTitleChange = vm::onTitleChange,
            onStart = {
                if (state.startInput == RecordingInput.WEARABLE) vm.startWearable() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            },
            onInputChange = vm::onInputChange,
            onPause = vm::pause,
            onResume = vm::resume,
            onStop = vm::stop,
            onWifiOnlyChange = vm::onWifiOnlyChange,
            onDelete = vm::delete,
            onDismissError = vm::dismissDeleteError,
            wearableCard = wearableCard,
            onOpenRecording = { onOpenSpeakers(it.id, it.title.ifBlank { untitled }) },
            onRetryGatewayRecent = vm::refreshGatewayRecent,
            modifier = Modifier.padding(padding),
        )
    }
}

/**
 * The screen's content, taking its state as arguments so a screenshot test
 * renders every state without a service, a microphone or a gateway. Paints
 * its own background (see `WatchesContent`).
 */
@Composable
fun RecordingContent(
    state: RecordingUiState,
    onTitleChange: (String) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onDismissError: () -> Unit,
    onOpenRecording: (GatewayRecording) -> Unit,
    onRetryGatewayRecent: () -> Unit,
    modifier: Modifier = Modifier,
    onInputChange: (RecordingInput) -> Unit = {},
    wearableCard: (@Composable () -> Unit)? = null,
) {
    val c = OmTheme.colors
    LazyColumn(
        modifier = modifier.fillMaxSize().background(c.bgPrimary),
        verticalArrangement = Arrangement.spacedBy(OmSpacing.md),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(OmSpacing.lg),
    ) {
        val recording = state.recording
        if (recording != null) {
            item { RecorderCard(recording, state.elapsedMs, state.wearableName, onPause, onResume, onStop) }
        }
        // While a wearable captures all day, a recording can still be started: it takes the episode's place.
        if (recording == null || recording.ambient) {
            item {
                if (state.availability == RecordingAvailability.Availability.AVAILABLE) {
                    StartCard(state, onTitleChange, onStart, onInputChange, onWifiOnlyChange)
                } else {
                    UnavailableCard(state.availability)
                }
            }
        }
        if (wearableCard != null && state.availability == RecordingAvailability.Availability.AVAILABLE) {
            item { wearableCard() }
        }
        state.deleteError?.let { message ->
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(message), color = c.danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismissError) { Text(stringResource(R.string.recording_dismiss)) }
                }
            }
        }
        if (state.recent.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.recording_on_this_phone),
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = OmSpacing.sm),
                )
            }
            items(state.recent, key = { it.id }) { recent -> RecentRow(recent, onDelete) }
        }
        val gateway = state.gatewayRecent
        if (gateway !is GatewayRecent.Hidden) {
            item {
                Text(
                    stringResource(R.string.recording_recent_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = OmSpacing.sm),
                )
            }
            when (gateway) {
                GatewayRecent.Loading -> item { QuietLine(stringResource(R.string.recording_recent_loading)) }
                GatewayRecent.Failed -> item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QuietLine(stringResource(R.string.recording_recent_failed), Modifier.weight(1f))
                        TextButton(onClick = onRetryGatewayRecent) { Text(stringResource(R.string.recording_retry)) }
                    }
                }
                is GatewayRecent.Loaded -> if (gateway.recordings.isEmpty()) {
                    item { QuietLine(stringResource(R.string.recording_recent_empty)) }
                } else {
                    items(gateway.recordings, key = { "gateway-${it.id}" }) { recording ->
                        GatewayRecordingRow(recording, onOpenRecording)
                    }
                }
                GatewayRecent.Hidden -> Unit
            }
        }
    }
}

@Composable
private fun QuietLine(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = OmTheme.colors.textMuted, modifier = modifier)
}

@Composable
private fun GatewayRecordingRow(recording: GatewayRecording, onOpen: (GatewayRecording) -> Unit) {
    val c = OmTheme.colors
    val res = resources()
    OmnesisCard(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(OmRadius.large)).clickable { onOpen(recording) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.RecordVoiceOver, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(OmSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    recording.title.ifBlank { stringResource(R.string.recording_untitled) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${recording.when_} · ${gatewayStateLine(res, recording.state)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                speakersLine(res, recording)?.let { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (recording.namedSpeakers < recording.speakers) c.accent else c.textSecondary,
                        maxLines = 1,
                    )
                }
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(20.dp))
        }
    }
}

/** The resources [stringResource] reads, following configuration changes. */
@Composable
@ReadOnlyComposable
internal fun resources(): Resources {
    LocalConfiguration.current
    return LocalContext.current.resources
}

internal fun gatewayStateLine(res: Resources, state: String): String = when (state) {
    "recording" -> res.getString(R.string.recording_gateway_state_recording)
    "transcribing" -> res.getString(R.string.recording_gateway_state_transcribing)
    "done" -> res.getString(R.string.recording_gateway_state_done)
    else -> state.replaceFirstChar { it.uppercase() }
}

/** "2 of 3 speakers named", or null when the gateway did not tell speakers apart. */
internal fun speakersLine(res: Resources, recording: GatewayRecording): String? = when {
    recording.speakers <= 0 -> null
    recording.namedSpeakers >= recording.speakers ->
        res.getQuantityString(R.plurals.recording_speakers_all_named, recording.speakers, recording.speakers)
    else -> res.getQuantityString(
        R.plurals.recording_speakers_some_named,
        recording.speakers,
        recording.namedSpeakers,
        recording.speakers,
    )
}

@Composable
private fun StartCard(
    state: RecordingUiState,
    onTitleChange: (String) -> Unit,
    onStart: () -> Unit,
    onInputChange: (RecordingInput) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
) {
    val c = OmTheme.colors
    val wearable = state.wearableName
    OmnesisCard(padding = OmSpacing.lg) {
        Text(stringResource(R.string.recording_start_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        Spacer(Modifier.height(OmSpacing.xs))
        Text(
            if (state.startInput == RecordingInput.WEARABLE) {
                stringResource(R.string.recording_start_body_wearable, wearable.orEmpty())
            } else {
                stringResource(R.string.recording_start_body_phone)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
        )
        if (wearable != null && state.wearableReady) {
            Spacer(Modifier.height(OmSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(OmSpacing.sm)) {
                FilterChip(
                    selected = state.startInput == RecordingInput.PHONE,
                    onClick = { onInputChange(RecordingInput.PHONE) },
                    leadingIcon = if (state.startInput == RecordingInput.PHONE) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                    label = { Text(stringResource(R.string.recording_input_phone)) },
                )
                FilterChip(
                    selected = state.startInput == RecordingInput.WEARABLE,
                    onClick = { onInputChange(RecordingInput.WEARABLE) },
                    leadingIcon = if (state.startInput == RecordingInput.WEARABLE) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                    label = { Text(wearable) },
                )
            }
            Spacer(Modifier.height(OmSpacing.xs))
            Text(
                stringResource(R.string.recording_input_hint, wearable),
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
            )
        }
        Spacer(Modifier.height(OmSpacing.md))
        OutlinedTextField(
            value = state.title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.recording_title_field)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(OmSpacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.recording_wifi_only), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                Text(
                    stringResource(R.string.recording_wifi_only_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textMuted,
                )
            }
            Switch(checked = state.wifiOnly, onCheckedChange = onWifiOnlyChange)
        }
        if (state.micDenied) {
            Spacer(Modifier.height(OmSpacing.sm))
            Text(
                stringResource(R.string.recording_mic_denied),
                style = MaterialTheme.typography.bodySmall,
                color = c.danger,
            )
        }
        Spacer(Modifier.height(OmSpacing.md))
        Button(onClick = onStart, enabled = !state.starting, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(OmSpacing.sm))
            Text(stringResource(if (state.starting) R.string.recording_starting else R.string.recording_start))
        }
        Spacer(Modifier.height(OmSpacing.sm))
        Text(
            stringResource(R.string.recording_consent_hint),
            style = MaterialTheme.typography.bodySmall,
            color = c.textMuted,
        )
    }
}

@Composable
private fun RecorderCard(
    recording: RecordingState.Active,
    elapsedMs: Long,
    wearableName: String?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    val c = OmTheme.colors
    val wearable = wearableName ?: stringResource(R.string.recording_wearable_fallback)
    OmnesisCard(padding = OmSpacing.lg) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(if (recording.paused) c.textMuted else c.danger, CircleShape),
            )
            Spacer(Modifier.width(OmSpacing.sm))
            Text(
                when {
                    recording.paused -> stringResource(R.string.recording_paused)
                    recording.ambient -> stringResource(R.string.recording_ambient_with, wearable)
                    recording.input == RecordingInput.WEARABLE -> stringResource(R.string.recording_with, wearable)
                    else -> stringResource(R.string.recording_in_progress)
                },
                style = MaterialTheme.typography.labelLarge,
                color = c.textSecondary,
            )
        }
        Spacer(Modifier.height(OmSpacing.md))
        Text(
            formatElapsed(elapsedMs),
            style = MaterialTheme.typography.displayMedium.copy(
                fontSize = 48.sp,
                fontWeight = FontWeight.Light,
                fontFeatureSettings = "tnum",
            ),
            color = c.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        recording.title?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (recording.micLost) {
            Spacer(Modifier.height(OmSpacing.sm))
            Text(
                if (recording.input == RecordingInput.WEARABLE) {
                    stringResource(R.string.recording_wearable_lost, wearable)
                } else {
                    stringResource(R.string.recording_mic_busy)
                },
                style = MaterialTheme.typography.bodySmall,
                color = c.danger,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (recording.ambient) {
            Spacer(Modifier.height(OmSpacing.sm))
            Text(
                stringResource(R.string.recording_ambient_hint, wearable),
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(OmSpacing.xs))
        Text(
            pluralStringResource(R.plurals.recording_parts_saved, recording.chunks, recording.chunks),
            style = MaterialTheme.typography.bodySmall,
            color = c.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(OmSpacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(OmSpacing.md)) {
            OutlinedButton(onClick = if (recording.paused) onResume else onPause, modifier = Modifier.weight(1f)) {
                Icon(
                    if (recording.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(OmSpacing.xs))
                Text(stringResource(if (recording.paused) R.string.recording_resume else R.string.recording_pause))
            }
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = c.danger, contentColor = Color.White),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(OmSpacing.xs))
                // Stopping an episode turns all-day capture off: the next would open at once.
                Text(stringResource(if (recording.ambient) R.string.recording_turn_off else R.string.recording_stop))
            }
        }
    }
}

@Composable
private fun UnavailableCard(availability: RecordingAvailability.Availability) {
    val c = OmTheme.colors
    OmnesisCard(padding = OmSpacing.lg) {
        Text(stringResource(R.string.recording_unavailable_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        Spacer(Modifier.height(OmSpacing.xs))
        Text(
            when (availability) {
                RecordingAvailability.Availability.TRANSCRIPTION_OFF ->
                    stringResource(R.string.recording_unavailable_transcription_off)
                else ->
                    stringResource(R.string.recording_unavailable_gateway)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
        )
    }
}

@Composable
private fun RecentRow(recent: RecentRecording, onDelete: (String) -> Unit) {
    val c = OmTheme.colors
    OmnesisCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (icon, tint) = when (recent.status) {
                is RecentRecording.Status.Uploading -> Icons.Outlined.CloudUpload to c.accent
                RecentRecording.Status.Sent -> Icons.Outlined.CloudDone to c.success
                is RecentRecording.Status.Refused -> Icons.Outlined.ErrorOutline to c.danger
            }
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(OmSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    recent.title.ifBlank { stringResource(R.string.recording_untitled) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${recent.when_} · ${statusLine(resources(), recent.status)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { onDelete(recent.id) }) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.recording_delete), tint = c.textMuted)
            }
        }
    }
}

internal fun statusLine(res: Resources, status: RecentRecording.Status): String = when (status) {
    is RecentRecording.Status.Uploading -> when (status.partsLeft) {
        0 -> res.getString(R.string.recording_status_waiting)
        else -> res.getQuantityString(R.plurals.recording_status_uploading, status.partsLeft, status.partsLeft)
    }
    RecentRecording.Status.Sent -> res.getString(R.string.recording_status_sent)
    // The reason is the gateway's own words, as it sent them.
    is RecentRecording.Status.Refused -> res.getString(R.string.recording_status_refused, status.reason)
}

/** `m:ss`, or `h:mm:ss` from the first hour. */
internal fun formatElapsed(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
