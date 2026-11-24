// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Lorenzo Farnararo

package dev.omnesis.android.feature.assist.ui

import dev.omnesis.android.feature.assist.CommandRoute
import dev.omnesis.android.feature.assist.Destination
import dev.omnesis.android.feature.assist.R
import dev.omnesis.android.feature.assist.WakeWord
import dev.omnesis.android.feature.assist.WakeWordModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandsStateTest {
    private val word = WakeWordModelCatalog.OK_HOME
    private val custom = WakeWord("custom:ehi_casa.onnx", "ehi casa", "ehi_casa.onnx", download = null, origin = R.string.assist_origin_imported)

    private fun row(destination: Destination, word: WakeWord? = this.word, installed: Boolean = true) =
        RouteRow(CommandRoute("r", word?.id ?: "gone", destination), word, installed)

    @Test fun aRouteSaysWhatItLacksFirst() {
        val state = VoiceCommandsState()
        assertEquals(R.string.assist_problem_word_gone, state.problem(row(Destination.HomeAssistant, word = null)))
        assertEquals(R.string.assist_problem_import_missing, state.problem(row(Destination.HomeAssistant, custom, installed = false)))
        assertEquals(R.string.assist_problem_download, state.problem(row(Destination.HomeAssistant, installed = false)))
        assertEquals(R.string.assist_problem_home_assistant, state.problem(row(Destination.HomeAssistant)))
        assertEquals(R.string.assist_problem_webhook, state.problem(row(Destination.Webhook("ftp://nope"))))
        assertEquals(VoiceCommandsState.OVERLAY_NEEDED, state.problem(row(Destination.PhoneAction)))
        assertFalse(state.canEnable)
    }

    @Test fun aReadyRouteHasNoProblem() {
        val state = VoiceCommandsState(baseUrl = "http://ha.local:8123", hasToken = true, overlayAllowed = true)
        assertNull(state.problem(row(Destination.HomeAssistant)))
        assertNull(state.problem(row(Destination.Webhook("https://example.com/hook"))))
        assertNull(state.problem(row(Destination.PhoneAction)))
        assertTrue(state.copy(routes = listOf(row(Destination.PhoneAction))).canEnable)
    }
}
