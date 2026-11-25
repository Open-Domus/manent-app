// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.manentlabs.app.gateway.Gateway
import it.manentlabs.app.gateway.PairingResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The pairing screen: one attempt at a time, the last one's outcome kept for display. */
@HiltViewModel
class PairingViewModel @Inject constructor(private val gateway: Gateway) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** The last attempt's outcome; [PairingResult.Paired] tells the screen to close. */
    private val _outcome = MutableStateFlow<PairingResult?>(null)
    val outcome: StateFlow<PairingResult?> = _outcome.asStateFlow()

    fun pairFromPayload(payload: String) = attempt { gateway.pairFromPayload(payload) }

    fun pairManually(url: String, code: String, fingerprint: String) =
        attempt { gateway.pairManually(url, code, fingerprint) }

    private fun attempt(block: suspend () -> PairingResult) {
        _busy.value = true
        _outcome.value = null
        viewModelScope.launch {
            val result = block()
            _outcome.value = result.takeUnless { it == PairingResult.Superseded }
            _busy.value = false
        }
    }
}
