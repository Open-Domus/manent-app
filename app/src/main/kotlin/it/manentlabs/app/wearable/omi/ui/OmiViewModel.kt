// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable.omi.ui

import android.content.Intent
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import it.manentlabs.app.R
import it.manentlabs.app.wearable.RecorderControl
import it.manentlabs.app.wearable.omi.HapticPattern
import it.manentlabs.app.wearable.omi.OmiDetails
import it.manentlabs.app.wearable.omi.OmiLink
import it.manentlabs.app.wearable.omi.OmiPairing
import it.manentlabs.app.wearable.omi.OmiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** A message for the card: one of ours, or the system's own words. */
sealed interface CardMessage {
    data class Text(val text: TextRes) : CardMessage
    data class System(val text: String) : CardMessage
}

/** The pendant card and the recordings row: state, pairing, and the details panel's actions. */
@HiltViewModel
class OmiViewModel @Inject constructor(
    private val link: OmiLink,
    private val pairing: OmiPairing,
    private val recorder: RecorderControl,
) : ViewModel() {
    val state: StateFlow<OmiState> = link.state
    val details: StateFlow<OmiDetails?> = link.details
    val captureAllDay: StateFlow<Boolean> = recorder.captureAllDay

    private val _associating = MutableStateFlow(false)
    val associating: StateFlow<Boolean> = _associating.asStateFlow()

    private val _error = MutableStateFlow<CardMessage?>(null)
    val error: StateFlow<CardMessage?> = _error.asStateFlow()

    private val _chooser = MutableStateFlow<IntentSender?>(null)

    /** The system chooser, waiting for the screen to launch it. */
    val chooser: StateFlow<IntentSender?> = _chooser.asStateFlow()

    init {
        pairing.rearm()
    }

    fun onPermissionDenied() {
        _error.value = CardMessage.Text(TextRes(R.string.omi_error_nearby_permission))
    }

    /** Call with the Nearby devices permission held. */
    fun associate() {
        _associating.value = true
        _error.value = null
        pairing.associate { result ->
            when (result) {
                is OmiPairing.Start.Chooser -> _chooser.value = result.sender
                OmiPairing.Start.Unsupported -> fail(CardMessage.Text(TextRes(R.string.omi_pairing_unsupported)))
                is OmiPairing.Start.Failed -> fail(
                    result.message?.let(CardMessage::System) ?: CardMessage.Text(TextRes(R.string.omi_pairing_not_found)),
                )
            }
        }
    }

    fun onChooserLaunched() {
        _chooser.value = null
    }

    fun onChooserResult(resultCode: Int, data: Intent?) {
        _associating.value = false
        pairing.onChooserResult(resultCode, data)
    }

    fun setCaptureAllDay(on: Boolean) = recorder.setCaptureAllDay(on)

    fun forget() {
        _error.value = null
        pairing.forget()
    }

    fun refreshDetails() = link.refreshDetails()

    fun find() = link.play(HapticPattern.FIND)

    private fun fail(message: CardMessage) {
        _associating.value = false
        _error.value = message
    }
}
