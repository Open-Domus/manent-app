// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable.omi

/** What the pendant's button reports. A long press switches it off and is never sent. */
enum class OmiButton { TAP, DOUBLE_TAP, PRESSED, RELEASED }

/** Optional hardware the firmware says it has, in the order of its feature bits. */
enum class OmiFeature {
    SPEAKER, ACCELEROMETER, BUTTON, BATTERY, USB, HAPTICS, OFFLINE_STORAGE, LED_DIMMING, MIC_GAIN,
}

/** The pendant's own flash: what it recorded while away from the phone. */
data class OmiStorage(
    val usedBytes: Long,
    val unreadPackets: Long,
    val freeBytes: Long,
    val clockSet: Boolean,
)

/**
 * Decoders for the values the pendant's characteristics carry. Each returns
 * null for a value it cannot make sense of, which the app treats as "not reported".
 */
object OmiValues {
    fun codec(value: ByteArray?): Int? = value?.firstOrNull()?.let { it.toInt() and 0xff }

    /** A percentage, or null for an empty or out-of-range value. */
    fun battery(value: ByteArray?): Int? = codec(value)?.takeIf { it in 0..100 }

    fun button(value: ByteArray?): OmiButton? = when (codec(value)) {
        1 -> OmiButton.TAP
        2 -> OmiButton.DOUBLE_TAP
        4 -> OmiButton.PRESSED
        5 -> OmiButton.RELEASED
        else -> null
    }

    /** The current time as the pendant's clock takes it: UTC epoch seconds, 32-bit little-endian. */
    fun timeSync(epochMillis: Long): ByteArray = uint32Le(epochMillis / 1000)

    /** A device-information string: UTF-8, without padding NULs or spaces. */
    fun text(value: ByteArray?): String? =
        value?.toString(Charsets.UTF_8)?.trim { it == '\u0000' || it == ' ' }?.takeIf { it.isNotEmpty() }

    fun features(value: ByteArray?): List<OmiFeature>? {
        if (value == null || value.isEmpty()) return null
        var mask = 0L
        for (i in 0 until minOf(4, value.size)) mask = mask or ((value[i].toLong() and 0xff) shl (8 * i))
        return OmiFeature.entries.filter { mask and (1L shl it.ordinal) != 0L }
    }

    fun storage(value: ByteArray?): OmiStorage? {
        if (value == null || value.size < 16) return null
        return OmiStorage(
            usedBytes = readUint32Le(value, 0),
            unreadPackets = readUint32Le(value, 4),
            freeBytes = readUint32Le(value, 8),
            clockSet = readUint32Le(value, 12) != 0L,
        )
    }

    private fun uint32Le(v: Long): ByteArray = ByteArray(4) { i -> (v ushr (8 * i)).toByte() }

    private fun readUint32Le(b: ByteArray, at: Int): Long =
        (0 until 4).fold(0L) { acc, i -> acc or ((b[at + i].toLong() and 0xff) shl (8 * i)) }
}
