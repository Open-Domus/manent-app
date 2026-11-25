// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable.omi.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.manentlabs.app.R
import it.manentlabs.app.ui.theme.Manent
import it.manentlabs.app.ui.theme.StatusLine
import it.manentlabs.app.ui.theme.StatusTone
import it.manentlabs.app.wearable.omi.OmiState

/** The pendant's one-line status and, once linked, a lock that says whether the link is encrypted. */
@Composable
fun OmiStatusRow(state: OmiState, modifier: Modifier = Modifier) {
    val tone = when (state) {
        is OmiState.Ready -> StatusTone.Good
        is OmiState.Unsupported -> StatusTone.Bad
        OmiState.Connecting -> StatusTone.Live
        else -> StatusTone.Idle
    }
    StatusLine(OmiText.status(state).resolve(), tone, modifier) {
        if (state is OmiState.Ready) {
            Spacer(Modifier.width(10.dp))
            if (state.encrypted) {
                Icon(Icons.Filled.Lock, stringResource(R.string.omi_link_encrypted), Modifier.size(15.dp), tint = Manent.colors.muted)
            } else {
                Icon(Icons.Filled.LockOpen, stringResource(R.string.omi_link_not_encrypted), Modifier.size(15.dp), tint = Manent.colors.danger)
            }
        }
    }
}
