// SPDX-License-Identifier: MIT

package it.manentlabs.app.speech

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.omnesis.android.feature.assist.AssistOutcome
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which of a destination's replies the phone reads aloud. */
enum class SpokenAnswers {
    /** None: the pendant's pulses are the only feedback. */
    OFF,

    /** Answers to questions ("the boiler is at 52 degrees"), not confirmations. */
    QUESTIONS,

    /** Every reply, confirmations ("turned on the light") included. */
    ALWAYS,
}

/** What to say aloud for [outcome] under [mode]; null to stay quiet. */
fun spokenReply(outcome: AssistOutcome, mode: SpokenAnswers): String? {
    val done = outcome as? AssistOutcome.Done ?: return null
    val speech = done.speech?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return when (mode) {
        SpokenAnswers.OFF -> null
        SpokenAnswers.QUESTIONS -> speech.takeIf { done.answered }
        SpokenAnswers.ALWAYS -> speech
    }
}

/** The chosen [SpokenAnswers], kept on the phone. */
@Singleton
class SpokenAnswerSettings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mode = MutableStateFlow(
        prefs.getString(KEY_MODE, null)
            ?.let { saved -> SpokenAnswers.entries.firstOrNull { it.name == saved } }
            ?: SpokenAnswers.QUESTIONS,
    )

    val state: StateFlow<SpokenAnswers> = mode.asStateFlow()

    fun set(value: SpokenAnswers) {
        prefs.edit().putString(KEY_MODE, value.name).apply()
        mode.value = value
    }

    private companion object {
        const val PREFS_NAME = "manent_spoken_answers"
        const val KEY_MODE = "mode"
    }
}
