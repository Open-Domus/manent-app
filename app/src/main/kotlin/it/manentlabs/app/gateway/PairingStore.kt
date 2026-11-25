// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

import android.annotation.SuppressLint
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import it.manentlabs.app.security.SecretBox
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Where the pairing and the install id live between runs. */
interface PairingStore {
    /** The stored pairing, or null when there is none or it cannot be read. */
    fun load(): Pairing?

    /** Replaces the stored pairing in one durable write. */
    fun save(pairing: Pairing)

    /** Forgets the pairing in one durable write, keeping the install id. */
    fun forget()

    /**
     * A random id made once per install. It survives unpairing, so the gateway
     * can give a re-paired phone back its old device entry.
     */
    fun installId(): String
}

/**
 * The [PairingStore] on disk: every value sealed with [SecretBox], in the file
 * and under the keys earlier builds used, so their pairings carry over.
 */
@Singleton
class SealedPairingStore @Inject constructor(@ApplicationContext context: Context) : PairingStore {
    private val prefs = context.getSharedPreferences("omnesis.pairing.sealed", Context.MODE_PRIVATE)

    override fun load(): Pairing? = SecretBox.open(prefs.getString(KEY_CREDENTIAL, null))?.let(CredentialCodec::decode)

    // A synchronous commit: the pairing must be on disk before the app relies on it.
    @SuppressLint("ApplySharedPref")
    override fun save(pairing: Pairing) {
        prefs.edit().putString(KEY_CREDENTIAL, SecretBox.seal(CredentialCodec.encode(pairing))).commit()
    }

    @SuppressLint("ApplySharedPref")
    @Synchronized
    override fun forget() {
        val installId = prefs.getString(KEY_INSTALL_ID, null)
        prefs.edit().clear().apply { installId?.let { putString(KEY_INSTALL_ID, it) } }.commit()
    }

    @SuppressLint("ApplySharedPref")
    @Synchronized
    override fun installId(): String {
        SecretBox.open(prefs.getString(KEY_INSTALL_ID, null))?.let { return it }
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_INSTALL_ID, SecretBox.seal(id)).commit()
        return id
    }

    private companion object {
        const val KEY_CREDENTIAL = "gateway.credential.v1"
        const val KEY_INSTALL_ID = "install.id"
    }
}
