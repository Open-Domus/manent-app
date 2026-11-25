// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Lorenzo Farnararo

package it.manentlabs.app.feature.omi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import it.manentlabs.app.designsystem.theme.OmSpacing
import it.manentlabs.app.designsystem.theme.OmTheme
import it.manentlabs.app.feature.omi.OmiDetails
import it.manentlabs.app.feature.omi.OmiStatus
import it.manentlabs.app.feature.omi.R
import java.util.Locale

/**
 * Everything the pendant reports about itself, in the order its owner looks
 * for it: what it is, how it is doing, how it is linked, its settings, what it
 * keeps on its own storage, and what its firmware supports. Stateless, so the
 * screenshot tests render it with invented values.
 */
@Composable
fun OmiDetailsContent(status: OmiStatus, details: OmiDetails?, onFind: () -> Unit) {
    val c = OmTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = OmSpacing.lg)
            .padding(bottom = OmSpacing.xl),
    ) {
        Text(stringResource(R.string.omi_pendant), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
        Spacer(Modifier.height(OmSpacing.xs))
        Text(omiStatusLine(status), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        if (details == null) {
            Spacer(Modifier.height(OmSpacing.lg))
            Text(
                stringResource(
                    if (status is OmiStatus.Ready) R.string.omi_details_reading else R.string.omi_details_when_connected,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textMuted,
            )
            return@Column
        }
        val res = LocalContext.current.resources
        Section(stringResource(R.string.omi_details_device)) {
            Row(stringResource(R.string.omi_details_name), details.name)
            Row(stringResource(R.string.omi_details_manufacturer), details.manufacturer)
            Row(stringResource(R.string.omi_details_model), details.model)
            Row(stringResource(R.string.omi_details_hardware), details.hardware)
            Row(stringResource(R.string.omi_details_firmware), details.firmware)
            Row(stringResource(R.string.omi_details_serial), details.serial)
            Row(stringResource(R.string.omi_details_address), details.address)
        }
        Section(stringResource(R.string.omi_details_status)) {
            Row(stringResource(R.string.omi_details_battery), details.battery?.let { "$it%" })
            Row(
                stringResource(R.string.omi_details_charging),
                details.charging?.let { stringResource(if (it) R.string.omi_details_yes else R.string.omi_details_no) },
            )
            Row(
                stringResource(R.string.omi_details_mute),
                stringResource(if (details.canMute) R.string.omi_details_supported else R.string.omi_details_mute_unsupported),
            )
        }
        Section(stringResource(R.string.omi_details_link)) {
            Row(
                stringResource(R.string.omi_details_encryption),
                stringResource(if (details.encrypted) R.string.omi_details_encrypted else R.string.omi_details_not_encrypted),
            )
            Row(
                stringResource(R.string.omi_details_signal),
                details.rssi?.let { stringResource(R.string.omi_details_signal_value, it, stringResource(signalWord(it))) },
            )
            Row(stringResource(R.string.omi_details_packet_size), details.mtu?.let { pluralStringResource(R.plurals.omi_bytes, it, it) })
            Row(stringResource(R.string.omi_details_audio), "Opus, 16 kHz mono")
            Row(stringResource(R.string.omi_details_frames_lost), details.framesLost?.toString())
        }
        Section(stringResource(R.string.omi_details_settings)) {
            Row(stringResource(R.string.omi_details_led), details.ledBrightness?.let { "$it%" })
            Row(stringResource(R.string.omi_details_mic_gain), details.micGain?.let { stringResource(R.string.omi_details_mic_gain_value, it) })
        }
        details.storage?.let { storage ->
            Section(stringResource(R.string.omi_details_storage)) {
                Row(stringResource(R.string.omi_details_used), bytes(res, storage.usedBytes))
                Row(stringResource(R.string.omi_details_free), bytes(res, storage.freeBytes))
                Row(
                    stringResource(R.string.omi_details_unread),
                    pluralStringResource(R.plurals.omi_packets, storage.unreadPackets.toPluralCount(), storage.unreadPackets),
                )
                Row(
                    stringResource(R.string.omi_details_clock),
                    stringResource(if (storage.clockSet) R.string.omi_details_clock_set else R.string.omi_details_clock_not_set),
                )
            }
            Spacer(Modifier.height(OmSpacing.sm))
            Text(
                stringResource(R.string.omi_details_storage_note),
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
            )
        }
        details.features?.let { features ->
            Section(stringResource(R.string.omi_details_features)) {
                val names = features.map { name -> featureLabel(name)?.let { stringResource(it) } ?: name }
                Text(
                    names.joinToString(" · ").ifEmpty { stringResource(R.string.omi_details_nothing_reported) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textPrimary,
                )
            }
        }
        Spacer(Modifier.height(OmSpacing.lg))
        OutlinedButton(onClick = onFind, enabled = status is OmiStatus.Ready, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.omi_details_find))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val c = OmTheme.colors
    Spacer(Modifier.height(OmSpacing.lg))
    Text(title.uppercase(Locale.ROOT), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
    Spacer(Modifier.height(OmSpacing.xs))
    HorizontalDivider(color = c.border)
    Column(verticalArrangement = Arrangement.spacedBy(OmSpacing.xs), modifier = Modifier.padding(top = OmSpacing.sm)) {
        content()
    }
}

/** A label and its value; a value the pendant did not report is left out. */
@Composable
private fun Row(label: String, value: String?) {
    value ?: return
    val c = OmTheme.colors
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(OmSpacing.md))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = c.textPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@StringRes
internal fun signalWord(rssi: Int): Int = when {
    rssi >= -60 -> R.string.omi_signal_strong
    rssi >= -75 -> R.string.omi_signal_good
    rssi >= -85 -> R.string.omi_signal_weak
    else -> R.string.omi_signal_very_weak
}

/** A size in the app's language: its decimal separator, and its word for bytes below a kilobyte. */
internal fun bytes(res: Resources, n: Long): String {
    val locale = res.configuration.locales[0]
    return when {
        n >= 1_000_000_000 -> String.format(locale, "%.1f GB", n / 1e9)
        n >= 1_000_000 -> String.format(locale, "%.1f MB", n / 1e6)
        n >= 1_000 -> String.format(locale, "%.0f kB", n / 1e3)
        else -> res.getQuantityString(R.plurals.omi_bytes, n.toInt(), n.toInt())
    }
}

/** A count for a plural, which takes an Int: past its range every language uses its "many" form anyway. */
private fun Long.toPluralCount(): Int = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

/** The firmware's feature names ([it.manentlabs.app.feature.omi.OmiProtocol.features]) in the app's language. */
@StringRes
internal fun featureLabel(name: String): Int? = when (name) {
    "Speaker" -> R.string.omi_feature_speaker
    "Accelerometer" -> R.string.omi_feature_accelerometer
    "Button" -> R.string.omi_feature_button
    "Battery" -> R.string.omi_feature_battery
    "USB" -> R.string.omi_feature_usb
    "Haptics" -> R.string.omi_feature_haptics
    "Offline storage" -> R.string.omi_feature_offline_storage
    "LED dimming" -> R.string.omi_feature_led_dimming
    "Microphone gain" -> R.string.omi_feature_mic_gain
    else -> null
}
