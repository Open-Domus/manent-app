// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable

import android.util.Log
import dev.omnesis.android.feature.assist.AssistOutcome
import dev.omnesis.android.feature.assist.AssistRuntime
import dev.omnesis.android.feature.recording.SharedWearableAudio
import it.manentlabs.app.speech.AnswerSpeaker
import it.manentlabs.app.speech.SpokenAnswerSettings
import it.manentlabs.app.speech.spokenReply
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Feeds the wearable's audio to the wake-word listener whenever the wearable
 * is linked and voice commands are on, through a view of the shared audio of
 * its own, so a recording running at the same time hears every frame too.
 * Whatever keeps the wearable linked (Omi's own foreground service, for one)
 * keeps the process, and so this, alive. A destination's reply is read aloud
 * on the phone as [SpokenAnswerSettings] says, after the pendant's pulse.
 */
@Singleton
class VoiceCommandsCoordinator @Inject constructor(
    private val wearable: Wearable,
    private val shared: SharedWearableAudio,
    private val assist: AssistRuntime,
    private val spoken: SpokenAnswerSettings,
    private val speaker: AnswerSpeaker,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false

    fun start() {
        if (started) return
        started = true
        val commandAudio = shared.view()
        scope.launch {
            combine(wearable.audio.ready, assist.changes) { ready, _ -> ready }.collect { ready ->
                assist.sync(
                    commandAudio,
                    ready,
                    onOutcome = ::onOutcome,
                    feedback = wearable::signal,
                )
            }
        }
    }

    private fun onOutcome(outcome: AssistOutcome) {
        when (outcome) {
            is AssistOutcome.Failed -> Log.w(TAG, "Command failed: ${outcome.reason}")
            is AssistOutcome.NotUnderstood -> Log.i(TAG, "Command not understood: ${outcome.reason}")
            is AssistOutcome.Done -> Log.i(TAG, "Command done (${if (outcome.answered) "answer" else "confirmation"})")
        }
        spokenReply(outcome, spoken.state.value)?.let(speaker::speak)
    }

    private companion object {
        const val TAG = "Manent:Assist"
    }
}
