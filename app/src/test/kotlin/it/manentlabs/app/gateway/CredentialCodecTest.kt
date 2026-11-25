// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CredentialCodecTest {
    private val pin = "cd".repeat(32)

    private fun stored(mode: String, url: String = "https://gw.lan/base", extra: String = ""","fingerprint":"$pin"""") =
        """{"url":"$url","token":"tok","pairingGeneration":"gen","accountId":"local","deviceId":"dev","name":"Home",""" +
            """"scopes":["read"],"tlsMode":"$mode"$extra,"directCandidates":[{"address":"10.0.0.2","port":443}],""" +
            """"deviceKeyRecorded":true,"tokenRotatedAt":123,"futureField":{"a":1}}"""

    @Test
    fun `reads a credential written by an earlier build`() {
        val pairing = CredentialCodec.decode(stored("pinned-leaf"))!!
        assertEquals("https://gw.lan/base", pairing.url)
        assertEquals("tok", pairing.token)
        assertEquals("gen", pairing.generation)
        assertEquals("dev", pairing.deviceId)
        assertEquals("Home", pairing.name)
        assertEquals(listOf("read"), pairing.scopes)
        assertEquals(TlsTrust(TlsMode.PINNED_LEAF, pin), pairing.trust)
        assertEquals("gen", pairing.key)
    }

    @Test
    fun `round trips with the stored field names`() {
        val pairing = Pairing("https://gw.lan", "https://gw.lan".toHttpUrl(), "t", null, "d", "", emptyList(), TlsTrust(TlsMode.LEGACY, null))
        val text = CredentialCodec.encode(pairing)
        val json = Json.parseToJsonElement(text) as JsonObject
        assertEquals("local", json["accountId"]!!.jsonPrimitive.content)
        assertEquals("legacy", json["tlsMode"]!!.jsonPrimitive.content)
        assertFalse("pairingGeneration" in json)
        assertFalse("fingerprint" in json)
        assertEquals(pairing, CredentialCodec.decode(text))
        assertEquals("https://gw.lan\u001Fd", pairing.key)
    }

    @Test
    fun `system ignores a stored pin but needs an origin`() {
        assertEquals(TlsTrust(TlsMode.SYSTEM, null), CredentialCodec.decode(stored("system", url = "https://gw.example.com"))!!.trust)
        assertNull(CredentialCodec.decode(stored("system")))
    }

    @Test
    fun `unusable credentials read as unpaired`() {
        assertNull(CredentialCodec.decode("not json"))
        assertNull(CredentialCodec.decode("{}"))
        assertNull(CredentialCodec.decode(stored("pinned-leaf", url = "http://gw.lan")))
        assertNull(CredentialCodec.decode(stored("pinned-leaf-strict", extra = ""","fingerprint":"AB"""")))
        assertNull(CredentialCodec.decode(stored("pinned-leaf", extra = "")))
        assertNull(CredentialCodec.decode(stored("pinned-key-strict", extra = ""","spkiFingerprint":"$pin"""")))
        assertNull(CredentialCodec.decode(stored("mystery")))
    }
}
