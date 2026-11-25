// SPDX-License-Identifier: MIT

package it.manentlabs.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.manentlabs.app.R
import it.manentlabs.app.gateway.ui.GatewaySection
import it.manentlabs.app.recording.ui.RecordingsSection
import it.manentlabs.app.ui.theme.Hairline
import it.manentlabs.app.ui.theme.Manent
import it.manentlabs.app.ui.theme.ManentPage
import it.manentlabs.app.voice.ui.VoiceSection
import it.manentlabs.app.wearable.omi.ui.OmiSection

/** One page, in numbered sections: the pendant, recordings, voice commands, the Omnesis gateway. */
@Composable
fun HomeScreen(onConnectGateway: () -> Unit, onRecordings: () -> Unit, onVoiceCommands: () -> Unit) {
    ManentPage {
        OmiSection(number = 1)
        RecordingsSection(number = 2, onOpen = onRecordings)
        VoiceSection(number = 3, onSettings = onVoiceCommands)
        GatewaySection(number = 4, onConnect = onConnectGateway)
        Footer()
    }
}

/** The app's name and version, small, under a hairline. */
@Composable
private fun Footer() {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    Column(Modifier.fillMaxWidth()) {
        Hairline()
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.home_footer, version).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Manent.colors.muted,
        )
    }
}
