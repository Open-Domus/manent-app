// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.manentlabs.app.gateway.Gateway
import it.manentlabs.app.gateway.PairedGateway
import it.manentlabs.app.gateway.StatusLine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The Omnesis card: the pairing, its status line, and checking or disconnecting. */
@HiltViewModel
class GatewayCardViewModel @Inject constructor(private val gateway: Gateway) : ViewModel() {
    val paired: StateFlow<PairedGateway?> = gateway.paired

    val statusLine: StateFlow<StatusLine> = gateway.status.map(StatusLine::of)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_DELAY_MS), StatusLine.of(gateway.status.value))

    init {
        // Every new pairing (and the one found at startup) is checked once.
        viewModelScope.launch {
            gateway.paired.filterNotNull().distinctUntilChanged().collect { gateway.refreshStatus() }
        }
    }

    fun check() {
        viewModelScope.launch { gateway.refreshStatus() }
    }

    fun disconnect() {
        viewModelScope.launch { gateway.unpair() }
    }

    private companion object {
        const val STOP_DELAY_MS = 5_000L
    }
}
