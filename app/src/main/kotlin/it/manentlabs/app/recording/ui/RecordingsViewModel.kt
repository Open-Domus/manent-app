// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.manentlabs.app.R
import it.manentlabs.app.gateway.Gateway
import it.manentlabs.app.gateway.GatewayError
import it.manentlabs.app.recording.InputChoice
import it.manentlabs.app.recording.LocalRecording
import it.manentlabs.app.recording.Outbox
import it.manentlabs.app.recording.Recorder
import it.manentlabs.app.recording.RecordingGate
import it.manentlabs.app.recording.RecordingJournal
import it.manentlabs.app.recording.RecordingSettings
import it.manentlabs.app.recording.RecordingStatus
import it.manentlabs.app.recording.StartRequest
import it.manentlabs.app.recording.UploadScheduler
import it.manentlabs.app.wearable.RecordingInput
import it.manentlabs.app.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class RecordingsViewModel @Inject constructor(
    private val recorder: Recorder,
    private val journal: RecordingJournal,
    private val outbox: Outbox,
    private val gateway: Gateway,
    private val settings: RecordingSettings,
    private val uploads: UploadScheduler,
    wearable: Wearable,
) : ViewModel() {
    val status: StateFlow<RecordingStatus> = recorder.status

    /** Whether recordings may start: a gateway is paired and takes them. */
    val gate: StateFlow<RecordingGate> = combine(gateway.paired, gateway.recordingAvailability) { paired, availability ->
        RecordingGate.of(paired != null, availability)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, recorder.gate)

    val wifiOnly: StateFlow<Boolean> = settings.wifiOnly
    val wearableName: String = wearable.audio.name
    val wearableReady: StateFlow<Boolean> = wearable.audio.ready

    /** The input Start would use now. */
    val input: StateFlow<RecordingInput> = combine(settings.input, wearable.audio.ready, InputChoice::effective)
        .stateIn(viewModelScope, SharingStarted.Eagerly, InputChoice.effective(settings.input.value, wearable.audio.ready.value))

    private val _local = MutableStateFlow<List<LocalRecording>>(emptyList())
    val local: StateFlow<List<LocalRecording>> = _local.asStateFlow()

    private val _deleteError = MutableStateFlow<Int?>(null)

    /** A string resource explaining why the last delete failed. */
    val deleteError: StateFlow<Int?> = _deleteError.asStateFlow()

    private val _starting = MutableStateFlow(false)
    val starting: StateFlow<Boolean> = _starting.asStateFlow()

    init {
        viewModelScope.launch {
            journal.recover().await()
            journal.sweep().await()
            uploads.request()
            refreshLocal()
        }
        viewModelScope.launch {
            status.collect { refreshLocal() }
        }
        viewModelScope.launch {
            while (true) {
                delay(LOCAL_REFRESH_MS)
                refreshLocal()
            }
        }
    }

    fun pickInput(input: RecordingInput) = settings.setInput(input)

    fun setWifiOnly(on: Boolean) {
        settings.setWifiOnly(on)
        uploads.restart()
    }

    /** Starts a recording from [input]; ignored while the gate is closed or while someone's recording runs. */
    fun start(title: String, input: RecordingInput) {
        if (gate.value != RecordingGate.OPEN) return
        val running = status.value as? RecordingStatus.Active
        if (running != null && !running.ambient) return
        _starting.value = true
        val accepted = recorder.start(StartRequest(input, title, ambient = false))
        viewModelScope.launch {
            if (accepted) {
                withTimeoutOrNull(STARTING_MS) {
                    status.first { it is RecordingStatus.Active && !it.ambient }
                }
            }
            _starting.value = false
        }
    }

    fun pause() = recorder.pause()

    fun resume() = recorder.resume()

    fun stop() = recorder.stopFromUser()

    /**
     * Deletes a recording waiting on this phone. One the gateway already
     * holds part of is deleted there first, so no half-sent copy stays behind.
     */
    fun delete(recording: LocalRecording) {
        viewModelScope.launch {
            val session = recording.session
            if (session.created) {
                val api = gateway.api.value
                if (api == null) {
                    _deleteError.value = R.string.recording_delete_reconnect
                    return@launch
                }
                try {
                    api.deleteRecording(session.id)
                } catch (_: GatewayError.NotFound) {
                } catch (_: GatewayError) {
                    _deleteError.value = R.string.recording_delete_failed
                    return@launch
                }
            }
            journal.delete(session.id).await()
            refreshLocal()
        }
    }

    fun dismissDeleteError() {
        _deleteError.value = null
    }

    private suspend fun refreshLocal() {
        val activeId = (status.value as? RecordingStatus.Active)?.sessionId
        val all = withContext(Dispatchers.IO) { outbox.localRecordings() }
        _local.value = all.filter { it.session.id != activeId }.sortedByDescending { it.session.startedAtMs }
    }

    private companion object {
        const val LOCAL_REFRESH_MS = 5_000L
        const val STARTING_MS = 5_000L
    }
}
