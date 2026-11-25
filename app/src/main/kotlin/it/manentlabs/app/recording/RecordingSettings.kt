// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import it.manentlabs.app.wearable.RecordingInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The recording choices the user makes on the Recordings screen, in the
 * preferences earlier builds used (`omnesis.recording`). "Capture all day"
 * lives in the same file but belongs to the wearable card.
 */
@Singleton
class RecordingSettings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("omnesis.recording", Context.MODE_PRIVATE)

    private val _wifiOnly = MutableStateFlow(prefs.getBoolean(KEY_WIFI_ONLY, false))

    /** Upload only over unmetered networks. Recording itself is unaffected. */
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    private val _input = MutableStateFlow(parseInput(prefs.getString(KEY_INPUT, null)))

    /** The input the user last picked; null when they never picked one. */
    val input: StateFlow<RecordingInput?> = _input.asStateFlow()

    fun setWifiOnly(on: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, on).apply()
        _wifiOnly.value = on
    }

    fun setInput(input: RecordingInput) {
        prefs.edit().putString(KEY_INPUT, input.name).apply()
        _input.value = input
    }

    private companion object {
        const val KEY_WIFI_ONLY = "wifiOnly"
        const val KEY_INPUT = "input"

        fun parseInput(value: String?): RecordingInput? =
            RecordingInput.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

/**
 * Which input Start records from: the wearable unless the user last picked
 * the phone, and the wearable only while it is linked.
 */
object InputChoice {
    fun preferred(picked: RecordingInput?): RecordingInput = picked ?: RecordingInput.WEARABLE

    fun effective(picked: RecordingInput?, wearableReady: Boolean): RecordingInput =
        if (preferred(picked) == RecordingInput.WEARABLE && wearableReady) RecordingInput.WEARABLE else RecordingInput.PHONE
}
