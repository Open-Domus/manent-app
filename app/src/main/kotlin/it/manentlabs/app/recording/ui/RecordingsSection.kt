// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.manentlabs.app.R
import it.manentlabs.app.gateway.Gateway
import it.manentlabs.app.recording.ElapsedFormat
import it.manentlabs.app.recording.Outbox
import it.manentlabs.app.recording.Recorder
import it.manentlabs.app.recording.RecordingGate
import it.manentlabs.app.recording.RecordingStatus
import it.manentlabs.app.ui.theme.Fine
import it.manentlabs.app.ui.theme.Manent
import it.manentlabs.app.ui.theme.ManentText
import it.manentlabs.app.ui.theme.PrimaryButton
import it.manentlabs.app.ui.theme.SecondaryButton
import it.manentlabs.app.ui.theme.Section
import it.manentlabs.app.ui.theme.StatusLine
import it.manentlabs.app.ui.theme.StatusTone
import it.manentlabs.app.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** What Home says about recordings: the one running, how many wait to be sent, or why none can start. */
@HiltViewModel
class RecordingsSummaryViewModel @Inject constructor(
    recorder: Recorder,
    outbox: Outbox,
    gateway: Gateway,
    wearable: Wearable,
) : ViewModel() {
    val status: StateFlow<RecordingStatus> = recorder.status
    val wearableName: String = wearable.audio.name

    val gate: StateFlow<RecordingGate> = combine(gateway.paired, gateway.recordingAvailability) { paired, availability ->
        RecordingGate.of(paired != null, availability)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_DELAY_MS), recorder.gate)

    /** Recordings on this phone not yet sent, the running one aside; checked every few seconds. */
    val waiting: StateFlow<Int> = combine(
        flow {
            while (true) {
                emit(outbox.localRecordings())
                delay(REFRESH_MS)
            }
        }.flowOn(Dispatchers.IO),
        recorder.status,
    ) { local, status ->
        val activeId = (status as? RecordingStatus.Active)?.sessionId
        local.count { it.session.id != activeId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_DELAY_MS), 0)

    private companion object {
        const val REFRESH_MS = 5_000L
        const val STOP_DELAY_MS = 5_000L
    }
}

/** Recordings on Home, with the way into the Recordings screen. */
@Composable
fun RecordingsSection(
    number: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecordingsSummaryViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val gate by viewModel.gate.collectAsStateWithLifecycle()
    val waiting by viewModel.waiting.collectAsStateWithLifecycle()
    val active = status as? RecordingStatus.Active

    Section(
        number = number,
        label = stringResource(R.string.home_recordings_label),
        title = stringResource(R.string.home_recordings_title),
        lede = if (active == null && gate == RecordingGate.OPEN) stringResource(R.string.home_recordings_lede) else null,
        modifier = modifier,
    ) {
        when {
            active != null -> Running(active, viewModel.wearableName)
            gate != RecordingGate.OPEN -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusLine(stringResource(R.string.recording_unavailable_title), StatusTone.Warning)
                Fine(stringResource(unavailableReason(gate)))
            }
        }
        if (waiting > 0) {
            StatusLine(pluralStringResource(R.plurals.home_recordings_waiting, waiting, waiting), StatusTone.Idle)
        }
        if (active == null && gate == RecordingGate.OPEN) {
            PrimaryButton(stringResource(R.string.home_recordings_record), onOpen)
        } else {
            SecondaryButton(stringResource(R.string.home_recordings_open), onOpen)
        }
    }
}

@Composable
private fun Running(status: RecordingStatus.Active, wearableName: String) {
    val elapsed = rememberElapsed(status)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusLine(recorderHeadline(status, wearableName), if (status.paused) StatusTone.Idle else StatusTone.Live)
        Text(
            ElapsedFormat.format(elapsed),
            style = ManentText.figure.copy(fontSize = 34.sp, lineHeight = 38.sp),
            color = Manent.colors.ink,
        )
        status.title?.let {
            Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, color = Manent.colors.ink)
        }
    }
}

internal fun unavailableReason(gate: RecordingGate): Int = when (gate) {
    RecordingGate.NOT_PAIRED -> R.string.recording_unavailable_not_paired
    RecordingGate.TRANSCRIPTION_OFF -> R.string.recording_unavailable_transcription_off
    else -> R.string.recording_unavailable_gateway
}
