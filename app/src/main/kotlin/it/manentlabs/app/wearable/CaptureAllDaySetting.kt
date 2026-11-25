// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The "Capture all day" switch. Stored where earlier builds kept it
 * (`omnesis.recording`, `alwaysOn`), so the choice survives the update.
 */
@Singleton
class CaptureAllDaySetting @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("omnesis.recording", Context.MODE_PRIVATE)
    private val _on = MutableStateFlow(prefs.getBoolean(KEY, false))
    val on: StateFlow<Boolean> = _on.asStateFlow()

    fun set(on: Boolean) {
        prefs.edit().putBoolean(KEY, on).apply()
        _on.value = on
    }

    private companion object {
        const val KEY = "alwaysOn"
    }
}
