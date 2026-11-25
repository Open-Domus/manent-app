// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable.omi.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import it.manentlabs.app.R
import it.manentlabs.app.wearable.omi.OmiFeature
import it.manentlabs.app.wearable.omi.OmiState
import java.util.Locale

/** A string resource and its arguments, chosen in plain code and resolved where a Context is at hand. */
data class TextRes(@StringRes val id: Int, val args: List<Any> = emptyList()) {
    fun resolve(context: Context): String =
        context.getString(id, *args.map { if (it is TextRes) it.resolve(context) else it }.toTypedArray())
}

@Composable
fun TextRes.resolve(): String = resolve(LocalContext.current)

/** A byte count for display: already formatted, or a plain count that takes the plural `omi_bytes`. */
sealed interface SizeText {
    data class Formatted(val text: String) : SizeText
    data class Bytes(val count: Int) : SizeText
}

/** Which words describe the pendant, for the card, the row, the details panel and the notification. */
object OmiText {
    /** The one-line status. */
    fun status(state: OmiState): TextRes = when (state) {
        OmiState.NotPaired -> TextRes(R.string.omi_status_not_paired)
        OmiState.Disconnected -> TextRes(R.string.omi_status_disconnected)
        OmiState.Connecting -> TextRes(R.string.omi_status_connecting)
        is OmiState.Ready -> state.battery
            ?.let { TextRes(R.string.omi_status_connected_battery, listOf(it)) }
            ?: TextRes(R.string.omi_status_connected)
        is OmiState.Unsupported -> TextRes(R.string.omi_status_unsupported)
    }

    /** The line under the status on the card: what the user can do, or what is wrong. Null when not paired. */
    fun detail(state: OmiState, captureAllDay: Boolean): TextRes? = when (state) {
        OmiState.NotPaired -> null
        OmiState.Disconnected -> TextRes(R.string.omi_detail_disconnected)
        OmiState.Connecting -> TextRes(R.string.omi_detail_connecting)
        is OmiState.Ready -> when {
            !state.encrypted -> TextRes(R.string.omi_detail_not_encrypted)
            captureAllDay -> TextRes(R.string.omi_detail_capturing)
            state.canMute -> TextRes(R.string.omi_detail_tap_mute)
            else -> TextRes(R.string.omi_detail_tap_no_mute)
        }
        is OmiState.Unsupported -> TextRes(
            R.string.omi_detail_unsupported,
            listOf(state.codec?.toString() ?: TextRes(R.string.omi_codec_unknown)),
        )
    }

    @StringRes
    fun signalWord(rssi: Int): Int = when {
        rssi >= -60 -> R.string.omi_signal_strong
        rssi >= -75 -> R.string.omi_signal_good
        rssi >= -85 -> R.string.omi_signal_weak
        else -> R.string.omi_signal_very_weak
    }

    /** Decimal units, with the locale's decimal separator. */
    fun size(bytes: Long, locale: Locale): SizeText = when {
        bytes >= 1_000_000_000 -> SizeText.Formatted(String.format(locale, "%.1f GB", bytes / 1e9))
        bytes >= 1_000_000 -> SizeText.Formatted(String.format(locale, "%.1f MB", bytes / 1e6))
        bytes >= 1_000 -> SizeText.Formatted(String.format(locale, "%.0f kB", bytes / 1e3))
        else -> SizeText.Bytes(pluralCount(bytes))
    }

    /** Plural resources take an Int. */
    fun pluralCount(n: Long): Int = n.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    @StringRes
    fun feature(feature: OmiFeature): Int = when (feature) {
        OmiFeature.SPEAKER -> R.string.omi_feature_speaker
        OmiFeature.ACCELEROMETER -> R.string.omi_feature_accelerometer
        OmiFeature.BUTTON -> R.string.omi_feature_button
        OmiFeature.BATTERY -> R.string.omi_feature_battery
        OmiFeature.USB -> R.string.omi_feature_usb
        OmiFeature.HAPTICS -> R.string.omi_feature_haptics
        OmiFeature.OFFLINE_STORAGE -> R.string.omi_feature_offline_storage
        OmiFeature.LED_DIMMING -> R.string.omi_feature_led_dimming
        OmiFeature.MIC_GAIN -> R.string.omi_feature_mic_gain
    }
}
