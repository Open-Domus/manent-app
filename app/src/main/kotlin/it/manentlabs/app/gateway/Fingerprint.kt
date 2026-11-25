// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

import java.security.MessageDigest

/** Certificate pins: SHA-256 written as 64 lowercase hex characters, nothing else. */
object Fingerprint {
    private val FORMAT = Regex("^[0-9a-f]{64}$")

    fun isValid(text: String): Boolean = FORMAT.matches(text)

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
