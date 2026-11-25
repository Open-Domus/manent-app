// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The gateway's last answer about recordings, kept per pairing so a recording
 * can start while the gateway is out of reach.
 */
interface AvailabilityMemory {
    /** What was last heard for the pairing with this key, or null if it was never asked. */
    fun recall(pairingKey: String): RecordingAvailability?

    fun remember(pairingKey: String, availability: RecordingAvailability)

    fun forget()
}

/**
 * [AvailabilityMemory] in plain preferences. Only the current pairing's answer
 * is kept, and the pairing key is stored hashed since it can hold a token id.
 */
@Singleton
class PrefsAvailabilityMemory @Inject constructor(@ApplicationContext context: Context) : AvailabilityMemory {
    private val prefs = context.getSharedPreferences("omnesis.recording.availability", Context.MODE_PRIVATE)

    override fun recall(pairingKey: String): RecordingAvailability? {
        if (prefs.getString(KEY_PAIRING, null) != hash(pairingKey)) return null
        val name = prefs.getString(KEY_AVAILABILITY, null) ?: return null
        return RecordingAvailability.entries.firstOrNull { it.name == name }
    }

    override fun remember(pairingKey: String, availability: RecordingAvailability) {
        prefs.edit().putString(KEY_PAIRING, hash(pairingKey)).putString(KEY_AVAILABILITY, availability.name).apply()
    }

    override fun forget() {
        prefs.edit().clear().apply()
    }

    private fun hash(key: String) = Fingerprint.sha256Hex(key.toByteArray(Charsets.UTF_8))

    private companion object {
        const val KEY_PAIRING = "pairing.sha256"
        const val KEY_AVAILABILITY = "availability"
    }
}
