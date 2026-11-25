// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable.omi

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which pendant this phone is associated with. The file and key names are
 * the ones earlier builds wrote, so an installed pairing survives the update.
 */
@Singleton
class OmiPairingStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("omnesis.omi", Context.MODE_PRIVATE)

    /** The pendant's MAC address, uppercased; null when none is associated. */
    val address: String? get() = prefs.getString(KEY_ADDRESS, null)

    /** The companion association id (Android 13+), or null when there is none. */
    val associationId: Int? get() = prefs.getInt(KEY_ASSOCIATION, NONE).takeIf { it != NONE }

    fun save(address: String, associationId: Int?) {
        prefs.edit()
            .putString(KEY_ADDRESS, address.uppercase())
            .putInt(KEY_ASSOCIATION, associationId ?: NONE)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_ADDRESS = "address"
        const val KEY_ASSOCIATION = "association"
        const val NONE = -1
    }
}
