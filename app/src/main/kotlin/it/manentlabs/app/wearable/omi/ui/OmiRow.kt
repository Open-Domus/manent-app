// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable.omi.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.manentlabs.app.R
import it.manentlabs.app.ui.theme.Eyebrow
import it.manentlabs.app.ui.theme.Hairline
import it.manentlabs.app.ui.theme.Manent
import it.manentlabs.app.ui.theme.TextAction
import it.manentlabs.app.wearable.omi.OmiState

/**
 * The pendant in one line between hairlines, for the Recordings screen.
 * [onManage] should lead to Home, where the pendant section is.
 */
@Composable
fun OmiRow(onManage: () -> Unit, modifier: Modifier = Modifier, viewModel: OmiViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier.fillMaxWidth()) {
        Hairline()
        Spacer(Modifier.height(12.dp))
        Eyebrow(stringResource(R.string.omi_pendant))
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state == OmiState.NotPaired) {
                Text(
                    stringResource(R.string.omi_row_intro),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Manent.colors.muted,
                )
                Spacer(Modifier.width(8.dp))
                TextAction(stringResource(R.string.omi_row_link_in_settings), onManage)
            } else {
                OmiStatusRow(state, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                TextAction(stringResource(R.string.omi_row_manage), onManage)
            }
        }
        Spacer(Modifier.height(4.dp))
        Hairline()
    }
}
