// SPDX-License-Identifier: MIT

package it.manentlabs.app.speech.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import it.manentlabs.app.R
import it.manentlabs.app.speech.AnswerSpeaker
import it.manentlabs.app.speech.SpokenAnswerSettings
import it.manentlabs.app.speech.SpokenAnswers
import it.manentlabs.app.ui.theme.Hairline
import it.manentlabs.app.ui.theme.OptionRow
import it.manentlabs.app.ui.theme.SecondaryButton
import it.manentlabs.app.ui.theme.Section
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Which replies the phone reads aloud, and a sample to hear how it sounds. */
@HiltViewModel
class SpokenAnswersViewModel @Inject constructor(
    private val settings: SpokenAnswerSettings,
    private val speaker: AnswerSpeaker,
) : ViewModel() {
    val mode: StateFlow<SpokenAnswers> = settings.state

    fun set(mode: SpokenAnswers) = settings.set(mode)

    fun speak(sample: String) = speaker.speak(sample)
}

/** Spoken answers, on the Voice commands screen: Home Assistant's replies read aloud. */
@Composable
fun SpokenAnswersSection(number: Int, modifier: Modifier = Modifier, viewModel: SpokenAnswersViewModel = hiltViewModel()) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val sample = stringResource(R.string.app_spoken_answers_sample)
    Section(
        number = number,
        label = stringResource(R.string.app_spoken_answers_label),
        title = stringResource(R.string.app_spoken_answers_title),
        lede = stringResource(R.string.app_spoken_answers_description),
        modifier = modifier,
    ) {
        Column(Modifier.selectableGroup()) {
            Hairline()
            for (option in SpokenAnswers.entries) {
                OptionRow(
                    title = stringResource(label(option)),
                    hint = stringResource(hint(option)),
                    selected = option == mode,
                    onClick = { viewModel.set(option) },
                )
                Hairline()
            }
        }
        SecondaryButton(stringResource(R.string.app_spoken_answers_try), onClick = { viewModel.speak(sample) }, enabled = mode != SpokenAnswers.OFF)
    }
}

private fun label(mode: SpokenAnswers) = when (mode) {
    SpokenAnswers.OFF -> R.string.app_spoken_answers_off
    SpokenAnswers.QUESTIONS -> R.string.app_spoken_answers_questions
    SpokenAnswers.ALWAYS -> R.string.app_spoken_answers_always
}

private fun hint(mode: SpokenAnswers) = when (mode) {
    SpokenAnswers.OFF -> R.string.app_spoken_answers_off_hint
    SpokenAnswers.QUESTIONS -> R.string.app_spoken_answers_questions_hint
    SpokenAnswers.ALWAYS -> R.string.app_spoken_answers_always_hint
}
