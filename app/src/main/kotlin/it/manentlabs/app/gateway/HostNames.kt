// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

import java.security.cert.X509Certificate

/**
 * The host rules behind the `pinned-leaf` public-trust fallback: a CA-issued
 * certificate is only worth anything for a real DNS name, and only its DNS
 * subject alternative names count.
 */
object HostNames {
    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    private const val SAN_DNS = 2

    /** True when [host] is a DNS name a public CA could have certified. */
    fun qualifiesForPublicTrust(host: String): Boolean {
        val name = normalize(host.removePrefix("[").removeSuffix("]"))
        return name.isNotEmpty() &&
            !IPV4.matches(name) &&
            ':' !in name &&
            name != "localhost" &&
            !name.endsWith(".localhost")
    }

    /**
     * True when [san] covers [host]: the same name, or `*.suffix` standing for
     * exactly one non-empty leftmost label.
     */
    fun sanMatches(san: String, host: String): Boolean {
        val pattern = normalize(san)
        val name = normalize(host)
        if (pattern.isEmpty() || name.isEmpty()) return false
        if (!pattern.startsWith("*.")) return pattern == name
        val suffix = pattern.substring(1)
        if (!name.endsWith(suffix)) return false
        val label = name.dropLast(suffix.length)
        return label.isNotEmpty() && '.' !in label
    }

    /** True when a DNS-type subject alternative name of [certificate] covers [host]. */
    fun certificateCovers(certificate: X509Certificate, host: String): Boolean {
        val names = try {
            certificate.subjectAlternativeNames
        } catch (_: java.security.cert.CertificateParsingException) {
            null
        } ?: return false
        return names.any { entry -> entry.size >= 2 && entry[0] == SAN_DNS && (entry[1] as? String)?.let { sanMatches(it, host) } == true }
    }

    private fun normalize(name: String) = name.removeSuffix(".").lowercase()
}
