// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostNamesTest {
    @Test
    fun `only real DNS names qualify for public trust`() {
        for (host in listOf("gw.example.com", "GW.Example.com.", "example")) {
            assertTrue(host, HostNames.qualifiesForPublicTrust(host))
        }
        for (host in listOf("", ".", "192.168.1.10", "[::1]", "fe80::1", "localhost", "LOCALHOST.", "box.localhost")) {
            assertFalse(host, HostNames.qualifiesForPublicTrust(host))
        }
    }

    @Test
    fun `exact SANs ignore case and a trailing dot`() {
        assertTrue(HostNames.sanMatches("GW.example.com.", "gw.example.COM"))
        assertFalse(HostNames.sanMatches("gw.example.com", "other.example.com"))
    }

    @Test
    fun `a wildcard stands for exactly one leftmost label`() {
        assertTrue(HostNames.sanMatches("*.example.com", "a.example.com"))
        assertTrue(HostNames.sanMatches("*.Example.com", "a.example.com."))
        assertFalse(HostNames.sanMatches("*.example.com", "example.com"))
        assertFalse(HostNames.sanMatches("*.example.com", "a.b.example.com"))
        assertFalse(HostNames.sanMatches("*.example.com", ".example.com"))
        assertFalse(HostNames.sanMatches("a*.example.com", "ab.example.com"))
    }
}
