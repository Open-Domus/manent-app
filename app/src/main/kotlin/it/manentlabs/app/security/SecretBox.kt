// SPDX-License-Identifier: MIT

package it.manentlabs.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.os.Build
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals short secrets (tokens, keys) with an AES-256-GCM key that never leaves
 * the Android Keystore. A sealed value reads `v1:<iv>:<ciphertext>` in Base64.
 *
 * The key needs no user authentication: uploads and voice commands read their
 * tokens with the phone locked. A value that does not open (another key, a
 * damaged string) reads as absent rather than failing.
 *
 * The alias is the one earlier releases used, so secrets they saved still open.
 */
object SecretBox {
    private const val ALIAS = "omnesis-prefs-key"
    private const val PREFIX = "v1"
    private const val TAG_BITS = 128

    /** Seals [plain]; throws [SealingFailed] when the Keystore will not, whatever its own error was. */
    fun seal(plain: String): String = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        listOf(PREFIX, b64(cipher.iv), b64(sealed)).joinToString(":")
    } catch (e: Exception) {
        throw SealingFailed(e)
    }

    class SealingFailed(cause: Throwable) : Exception("the Keystore could not seal a secret", cause)

    fun open(sealed: String?): String? {
        if (sealed == null) return null
        val parts = sealed.split(':')
        if (parts.size != 3 || parts[0] != PREFIX) return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, existingKey() ?: return null, GCMParameterSpec(TAG_BITS, unb64(parts[1])))
            String(cipher.doFinal(unb64(parts[2])), Charsets.UTF_8)
        } catch (e: Exception) {
            // GeneralSecurityException, a bad Base64 string, or the ProviderException
            // and IOException some Keystores throw: the value is simply not there.
            Log.w(TAG, "A sealed value did not open: ${e::class.simpleName}")
            null
        }
    }

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun existingKey(): SecretKey? = keyStore().getKey(ALIAS, null) as SecretKey?

    @Synchronized
    private fun key(): SecretKey = existingKey() ?: generate(strongBox = Build.VERSION.SDK_INT >= 28)

    private fun generate(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .apply { if (strongBox && Build.VERSION.SDK_INT >= 28) setIsStrongBoxBacked(true) }
            .build()
        return try {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(spec)
                generateKey()
            }
        } catch (e: Exception) {
            // No StrongBox on this phone (a ProviderException on some, a
            // GeneralSecurityException on others): the TEE holds the key instead.
            if (strongBox) generate(strongBox = false) else throw e
        }
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(text: String) = Base64.decode(text, Base64.NO_WRAP)

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG = "Manent:Secrets"
}
