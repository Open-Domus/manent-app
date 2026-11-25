// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.manentlabs.app.R
import it.manentlabs.app.ui.theme.Dropdown
import it.manentlabs.app.ui.theme.Fine
import it.manentlabs.app.ui.theme.Hairline
import it.manentlabs.app.ui.theme.Manent
import it.manentlabs.app.ui.theme.SecondaryButton
import it.manentlabs.app.ui.theme.Section
import it.manentlabs.app.voice.phone.PhoneActionKind

/** What a wake word for the phone can do, and with which app. */
@Composable
internal fun PhoneActionsSection(
    number: Int,
    rows: List<PhoneRow>,
    callingAllowed: Boolean,
    onApp: (PhoneActionKind, String) -> Unit,
    onAllowCalling: () -> Unit,
) {
    Section(
        number = number,
        label = stringResource(R.string.voice_section_phone_actions),
        title = stringResource(R.string.voice_phone_actions_title),
        lede = stringResource(R.string.voice_phone_actions_hint),
    ) {
        Column {
            Hairline()
            for (row in rows) {
                Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                VoiceText.action(row.kind),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (row.apps.isEmpty()) Manent.colors.muted else Manent.colors.ink,
                            )
                            Fine(VoiceText.example(row.kind))
                        }
                        Spacer(Modifier.width(12.dp))
                        AppChoice(row, onApp)
                    }
                    if (row.kind == PhoneActionKind.CALL && row.apps.isNotEmpty() && !callingAllowed) {
                        Fine(stringResource(R.string.voice_calling_needed), color = Manent.colors.warning)
                        SecondaryButton(stringResource(R.string.voice_allow), onAllowCalling)
                    }
                }
                Hairline()
            }
        }
    }
}

@Composable
private fun AppChoice(row: PhoneRow, onApp: (PhoneActionKind, String) -> Unit) {
    when {
        row.apps.isEmpty() -> Side(stringResource(R.string.voice_no_app))
        row.kind == PhoneActionKind.OPEN_APP -> Side(pluralStringResource(R.plurals.voice_app_count, row.apps.size, row.apps.size))
        row.kind.appChoosable && row.apps.size > 1 -> Column(Modifier.widthIn(max = 168.dp), horizontalAlignment = Alignment.End) {
            Dropdown(
                current = row.app?.label ?: stringResource(R.string.voice_choose_app),
                options = row.apps,
                optionLabel = { it.label },
                onPick = { onApp(row.kind, it.packageName) },
            )
            if (row.app == null) Fine(stringResource(R.string.voice_choose_app_warning), color = Manent.colors.warning)
        }
        else -> Side((row.app ?: row.apps.first()).label)
    }
}

/** The app that will act, or why none can, on the right of its action. */
@Composable
private fun Side(text: String) {
    Text(text, Modifier.widthIn(max = 150.dp), style = MaterialTheme.typography.bodySmall, color = Manent.colors.muted, textAlign = TextAlign.End)
}
