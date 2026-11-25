// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable.omi.ui

import it.manentlabs.app.R
import it.manentlabs.app.wearable.omi.OmiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class OmiTextTest {
    @Test
    fun `status line for each state`() {
        assertEquals(TextRes(R.string.omi_status_not_paired), OmiText.status(OmiState.NotPaired))
        assertEquals(TextRes(R.string.omi_status_disconnected), OmiText.status(OmiState.Disconnected))
        assertEquals(TextRes(R.string.omi_status_connecting), OmiText.status(OmiState.Connecting))
        assertEquals(TextRes(R.string.omi_status_connected_battery, listOf(42)), OmiText.status(OmiState.Ready(42, true, true)))
        assertEquals(TextRes(R.string.omi_status_connected), OmiText.status(OmiState.Ready(null, true, true)))
        assertEquals(TextRes(R.string.omi_status_unsupported), OmiText.status(OmiState.Unsupported(20)))
    }

    @Test
    fun `detail line for each state`() {
        assertNull(OmiText.detail(OmiState.NotPaired, false))
        assertEquals(TextRes(R.string.omi_detail_disconnected), OmiText.detail(OmiState.Disconnected, true))
        assertEquals(TextRes(R.string.omi_detail_connecting), OmiText.detail(OmiState.Connecting, false))
        assertEquals(TextRes(R.string.omi_detail_not_encrypted), OmiText.detail(OmiState.Ready(1, false, true), true))
        assertEquals(TextRes(R.string.omi_detail_capturing), OmiText.detail(OmiState.Ready(1, true, false), true))
        assertEquals(TextRes(R.string.omi_detail_tap_mute), OmiText.detail(OmiState.Ready(1, true, true), false))
        assertEquals(TextRes(R.string.omi_detail_tap_no_mute), OmiText.detail(OmiState.Ready(1, true, false), false))
        assertEquals(TextRes(R.string.omi_detail_unsupported, listOf("20")), OmiText.detail(OmiState.Unsupported(20), false))
        assertEquals(
            TextRes(R.string.omi_detail_unsupported, listOf(TextRes(R.string.omi_codec_unknown))),
            OmiText.detail(OmiState.Unsupported(null), false),
        )
    }

    @Test
    fun `signal word thresholds`() {
        assertEquals(R.string.omi_signal_strong, OmiText.signalWord(-60))
        assertEquals(R.string.omi_signal_good, OmiText.signalWord(-61))
        assertEquals(R.string.omi_signal_good, OmiText.signalWord(-75))
        assertEquals(R.string.omi_signal_weak, OmiText.signalWord(-85))
        assertEquals(R.string.omi_signal_very_weak, OmiText.signalWord(-86))
    }

    @Test
    fun `sizes use decimal units and the locale's separator`() {
        assertEquals(SizeText.Bytes(999), OmiText.size(999, Locale.US))
        assertEquals(SizeText.Formatted("1 kB"), OmiText.size(1_000, Locale.US))
        assertEquals(SizeText.Formatted("512 kB"), OmiText.size(511_600, Locale.US))
        assertEquals(SizeText.Formatted("1.5 MB"), OmiText.size(1_500_000, Locale.US))
        assertEquals(SizeText.Formatted("1,5 MB"), OmiText.size(1_500_000, Locale.ITALIAN))
        assertEquals(SizeText.Formatted("4.3 GB"), OmiText.size(4_294_967_295, Locale.US))
    }

    @Test
    fun `plural counts are capped at the largest Int`() {
        assertEquals(Int.MAX_VALUE, OmiText.pluralCount(4_294_967_295))
        assertEquals(3, OmiText.pluralCount(3))
    }
}
