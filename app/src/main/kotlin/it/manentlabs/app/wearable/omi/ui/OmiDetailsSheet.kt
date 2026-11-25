// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable.omi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import it.manentlabs.app.R
import it.manentlabs.app.ui.theme.Eyebrow
import it.manentlabs.app.ui.theme.FactRow
import it.manentlabs.app.ui.theme.Fine
import it.manentlabs.app.ui.theme.Hairline
import it.manentlabs.app.ui.theme.Manent
import it.manentlabs.app.ui.theme.ManentText
import it.manentlabs.app.ui.theme.PageGutter
import it.manentlabs.app.ui.theme.SecondaryButton
import it.manentlabs.app.ui.theme.SubHeading
import it.manentlabs.app.wearable.omi.OmiDetails
import it.manentlabs.app.wearable.omi.OmiState
import java.util.Locale

/** Everything the pendant reports about itself, and a button to make it buzz so it can be found. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmiDetailsSheet(state: OmiState, details: OmiDetails?, onFind: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RectangleShape,
        containerColor = Manent.colors.paper,
        tonalElevation = 0.dp,
        dragHandle = { Box(Modifier.padding(vertical = 14.dp).size(width = 36.dp, height = 3.dp).background(Manent.colors.rule)) },
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = PageGutter, end = PageGutter, bottom = 32.dp),
        ) {
            HorizontalDivider(thickness = 1.dp, color = Manent.colors.ink)
            Spacer(Modifier.height(12.dp))
            Eyebrow(stringResource(R.string.omi_details))
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.omi_pendant),
                style = MaterialTheme.typography.headlineSmall,
                color = Manent.colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(14.dp))
            OmiStatusRow(state)
            if (details == null) {
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(if (state is OmiState.Ready) R.string.omi_details_reading else R.string.omi_details_when_connected),
                    style = ManentText.lede,
                    color = Manent.colors.ink,
                )
            } else {
                DetailSections(details)
                Spacer(Modifier.height(28.dp))
                SecondaryButton(
                    stringResource(R.string.omi_details_find),
                    onClick = onFind,
                    enabled = state is OmiState.Ready,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DetailSections(d: OmiDetails) {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val yes = stringResource(R.string.omi_details_yes)
    val no = stringResource(R.string.omi_details_no)

    Section(R.string.omi_details_device) {
        Item(R.string.omi_details_name, d.name)
        Item(R.string.omi_details_manufacturer, d.manufacturer)
        Item(R.string.omi_details_model, d.model)
        Item(R.string.omi_details_hardware, d.hardware)
        Item(R.string.omi_details_firmware, d.firmware)
        Item(R.string.omi_details_serial, d.serial)
        Item(R.string.omi_details_address, d.address)
    }
    Section(R.string.omi_details_status) {
        Item(R.string.omi_details_battery, d.battery?.let { stringResource(R.string.omi_details_percent, it) })
        Item(R.string.omi_details_charging, d.charging?.let { if (it) yes else no })
        Item(
            R.string.omi_details_mute,
            stringResource(if (d.canMute) R.string.omi_details_supported else R.string.omi_details_mute_unsupported),
        )
    }
    Section(R.string.omi_details_link) {
        Item(
            R.string.omi_details_encryption,
            stringResource(if (d.encrypted) R.string.omi_details_encrypted else R.string.omi_details_not_encrypted),
        )
        Item(
            R.string.omi_details_signal,
            d.rssi?.let { stringResource(R.string.omi_details_signal_value, it, stringResource(OmiText.signalWord(it))) },
        )
        Item(R.string.omi_details_packet_size, d.mtu?.let { pluralStringResource(R.plurals.omi_bytes, it, it) })
        Item(R.string.omi_details_audio, stringResource(R.string.omi_details_audio_format))
        Item(R.string.omi_details_frames_lost, d.framesLost?.toString())
    }
    Section(R.string.omi_details_settings) {
        Item(R.string.omi_details_led, d.ledDim?.let { stringResource(R.string.omi_details_percent, it) })
        Item(R.string.omi_details_mic_gain, d.micGain?.let { stringResource(R.string.omi_details_mic_gain_value, it) })
    }
    d.storage?.let { storage ->
        Section(R.string.omi_details_storage) {
            Item(R.string.omi_details_used, size(storage.usedBytes, locale))
            Item(R.string.omi_details_free, size(storage.freeBytes, locale))
            val unread = OmiText.pluralCount(storage.unreadPackets)
            Item(R.string.omi_details_unread, pluralStringResource(R.plurals.omi_packets, unread, unread))
            Item(
                R.string.omi_details_clock,
                stringResource(if (storage.clockSet) R.string.omi_details_clock_set else R.string.omi_details_clock_not_set),
            )
            Spacer(Modifier.height(10.dp))
            Fine(stringResource(R.string.omi_details_storage_note))
        }
    }
    d.features?.let { features ->
        Section(R.string.omi_details_features) {
            val names = features.map { stringResource(OmiText.feature(it)) }
            Text(
                if (names.isEmpty()) stringResource(R.string.omi_details_nothing_reported) else names.joinToString(" · "),
                Modifier.padding(vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Manent.colors.ink,
            )
        }
    }
}

@Composable
private fun size(bytes: Long, locale: Locale): String = when (val s = OmiText.size(bytes, locale)) {
    is SizeText.Formatted -> s.text
    is SizeText.Bytes -> pluralStringResource(R.plurals.omi_bytes, s.count, s.count)
}

@Composable
private fun Section(title: Int, rows: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(28.dp))
    SubHeading(stringResource(title))
    Column(content = rows)
}

/** A label and its value over a hairline; left out entirely when the pendant did not report the value. */
@Composable
private fun Item(label: Int, value: String?) {
    if (value == null) return
    FactRow(stringResource(label), value)
    Hairline()
}
