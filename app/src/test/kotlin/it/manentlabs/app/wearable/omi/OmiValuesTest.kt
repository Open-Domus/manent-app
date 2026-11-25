// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable.omi

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OmiValuesTest {
    private fun bytes(vararg v: Int) = v.map { it.toByte() }.toByteArray()

    @Test
    fun `features map bits in order and ignore higher bits`() {
        assertEquals(
            listOf(OmiFeature.SPEAKER, OmiFeature.BUTTON, OmiFeature.MIC_GAIN),
            OmiValues.features(bytes(0b0000_0101, 0b0000_0001, 0, 0x80)),
        )
        assertEquals(OmiFeature.entries, OmiValues.features(bytes(0xff, 0xff, 0xff, 0xff, 0xff)))
        assertEquals(emptyList<OmiFeature>(), OmiValues.features(bytes(0)))
        assertNull(OmiValues.features(ByteArray(0)))
        assertEquals(listOf(OmiFeature.HAPTICS), OmiValues.features(bytes(0x20)))
    }

    @Test
    fun `storage is four little-endian unsigned words`() {
        val value = bytes(
            0x10, 0x27, 0, 0, // 10 000 used
            3, 0, 0, 0, // 3 unread
            0xff, 0xff, 0xff, 0xff, // 4 294 967 295 free
            0, 0, 0, 1, // clock set (non-zero)
        )
        assertEquals(OmiStorage(10_000, 3, 4_294_967_295, clockSet = true), OmiValues.storage(value))
        assertEquals(false, OmiValues.storage(ByteArray(16))?.clockSet)
        assertNull(OmiValues.storage(ByteArray(15)))
    }

    @Test
    fun `battery accepts only a percentage`() {
        assertEquals(87, OmiValues.battery(bytes(87)))
        assertEquals(100, OmiValues.battery(bytes(100)))
        assertNull(OmiValues.battery(bytes(101)))
        assertNull(OmiValues.battery(bytes(0xff)))
        assertNull(OmiValues.battery(ByteArray(0)))
    }

    @Test
    fun `button bytes map to events`() {
        assertEquals(OmiButton.TAP, OmiValues.button(bytes(1)))
        assertEquals(OmiButton.DOUBLE_TAP, OmiValues.button(bytes(2)))
        assertEquals(OmiButton.PRESSED, OmiValues.button(bytes(4)))
        assertEquals(OmiButton.RELEASED, OmiValues.button(bytes(5)))
        assertNull(OmiValues.button(bytes(3)))
    }

    @Test
    fun `time sync is epoch seconds little-endian`() {
        assertArrayEquals(bytes(0x78, 0x56, 0x34, 0x12), OmiValues.timeSync(0x12345678L * 1000 + 999))
    }

    @Test
    fun `device strings are trimmed of NULs and spaces`() {
        assertEquals("Omi DevKit 2", OmiValues.text(" Omi DevKit 2\u0000\u0000".toByteArray()))
        assertNull(OmiValues.text(bytes(0, 0x20, 0)))
    }

    @Test
    fun `codec is the first byte unsigned`() {
        assertEquals(21, OmiValues.codec(bytes(21, 0)))
        assertEquals(200, OmiValues.codec(bytes(200)))
        assertNull(OmiValues.codec(null))
    }
}
