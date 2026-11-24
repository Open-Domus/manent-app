// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Lorenzo Farnararo

package dev.omnesis.android.feature.recording.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.omnesis.android.designsystem.theme.OmTheme
import dev.omnesis.android.designsystem.theme.OmnesisTheme
import dev.omnesis.android.feature.recording.R
import dev.omnesis.android.transport.dto.PersonSummary
import dev.omnesis.android.transport.dto.RecordingSpeakerDto
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Render-review screenshots for the speakers of a recording: a mix of named
 * and unnamed speakers with one change saving, the person picker with
 * results, no speakers told apart, and a failed load, light + dark. All
 * fixture data is invented (privacy rule).
 *
 *   ./gradlew :feature-recording:recordRoborazziDebug   ->   feature-recording/src/test/roborazzi/
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class RecordingSpeakersScreenshotTest {

    private fun capture(name: String, dark: Boolean, body: @Composable () -> Unit) {
        captureRoboImage(filePath = "src/test/roborazzi/$name.png") {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                OmnesisTheme(darkTheme = dark) { body() }
            }
        }
    }

    @Composable
    private fun content(state: RecordingSpeakersUiState) {
        RecordingSpeakersContent(
            state = state,
            onRetry = {},
            onSetSelf = {},
            onChoosePerson = {},
            onName = {},
            onClear = {},
        )
    }

    private val speakers = listOf(
        RecordingSpeakerDto(
            speaker = 1,
            isSelf = true,
            talkMs = 252_000,
            samples = listOf("Shall we start with the timeline?", "I can take the follow-up on that."),
        ),
        RecordingSpeakerDto(
            speaker = 2,
            personId = "p-maya",
            name = "Maya",
            talkMs = 198_000,
            samples = listOf(
                "The draft is ready, but the second section still needs the numbers from the last review " +
                    "before anyone outside the team sees it.",
                "Sounds good to me.",
            ),
        ),
        RecordingSpeakerDto(
            speaker = 3,
            talkMs = 64_000,
            samples = listOf("Can we move the next check-in to Thursday?", "Great, thanks.", "See you then."),
        ),
        RecordingSpeakerDto(speaker = 4, talkMs = 12_000, samples = listOf("Hi, sorry I'm late.")),
    )

    private val loaded = RecordingSpeakersUiState(
        title = "Design sync",
        load = RecordingSpeakersUiState.Load.Loaded,
        speakers = speakers,
        saving = setOf(4),
    )

    @Test fun content_light() = capture("recording_speakers_light", false) { content(loaded) }
    @Test fun content_dark() = capture("recording_speakers_dark", true) { content(loaded) }

    private val empty = RecordingSpeakersUiState(load = RecordingSpeakersUiState.Load.Loaded)

    @Test fun empty_light() = capture("recording_speakers_empty_light", false) { content(empty) }
    @Test fun empty_dark() = capture("recording_speakers_empty_dark", true) { content(empty) }

    private val failed = RecordingSpeakersUiState(load = RecordingSpeakersUiState.Load.Failed(R.string.recording_speakers_load_failed))

    @Test fun error_light() = capture("recording_speakers_error_light", false) { content(failed) }
    @Test fun error_dark() = capture("recording_speakers_error_dark", true) { content(failed) }

    private val picker = PersonPickerState(
        speaker = 3,
        query = "ja",
        results = listOf(
            PersonSummary(id = "p-jamie", canonicalName = "Jamie", documentCount = 42),
            PersonSummary(id = "p-jade", canonicalName = "Jade", documentCount = 1),
            PersonSummary(id = "p-janek", canonicalName = "Janek", documentCount = 0),
        ),
        searching = false,
    )

    /** The sheet's body over the dimmed screen, as the modal sheet shows it. */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun sheet(state: PersonPickerState) {
        Box(Modifier.fillMaxSize()) {
            content(loaded)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)))
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(OmTheme.colors.bgSecondary),
            ) {
                BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
                PersonPickerContent(picker = state, onQueryChange = {}, onPick = {})
            }
        }
    }

    @Test fun picker_light() = capture("recording_speakers_picker_light", false) { sheet(picker) }
    @Test fun picker_dark() = capture("recording_speakers_picker_dark", true) { sheet(picker) }

    private val res = ApplicationProvider.getApplicationContext<Context>().resources

    @Test fun formatsTalkTime() {
        assertEquals("Under a minute", formatTalkTime(res, 5_000))
        assertEquals("1 min", formatTalkTime(res, 61_000))
        assertEquals("4 min", formatTalkTime(res, 240_000))
        assertEquals("1 h", formatTalkTime(res, 3_600_000))
        assertEquals("1 h 5 min", formatTalkTime(res, 3_900_000))
    }

    @Test fun labelsSpeakers() {
        assertEquals("You", speakerLabel(res, RecordingSpeakerDto(1, isSelf = true, name = "Me")))
        assertEquals("Jamie", speakerLabel(res, RecordingSpeakerDto(2, name = "Jamie")))
        assertEquals("Speaker 3", speakerLabel(res, RecordingSpeakerDto(3)))
    }
}
