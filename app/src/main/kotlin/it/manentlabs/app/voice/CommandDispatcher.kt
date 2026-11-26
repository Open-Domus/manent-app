// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice

import android.util.Log
import it.manentlabs.app.speech.AnswerSpeaker
import it.manentlabs.app.speech.SpokenAnswerSettings
import it.manentlabs.app.speech.spokenReply
import it.manentlabs.app.voice.hermes.HermesClient
import it.manentlabs.app.voice.homeassistant.HomeAssistantClient
import it.manentlabs.app.voice.phone.PhoneActions
import it.manentlabs.app.voice.phone.PhoneCommandParser
import it.manentlabs.app.voice.phone.PhonePermissions
import it.manentlabs.app.voice.routes.Destination
import it.manentlabs.app.voice.routes.Route
import it.manentlabs.app.voice.routes.VoiceSettings
import it.manentlabs.app.voice.transcribe.CommandTranscriber
import it.manentlabs.app.voice.webhook.WebhookSender
import it.manentlabs.app.wearable.Wearable
import it.manentlabs.app.wearable.WearableSignal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends a captured command to its route's destination, then tells the
 * wearer how it went: two short pulses for done, one long for anything
 * else. A reply worth hearing (see [spokenReply]) is read aloud after.
 */
@Singleton
class CommandDispatcher @Inject constructor(
    private val settings: VoiceSettings,
    private val homeAssistant: HomeAssistantClient,
    private val webhooks: WebhookSender,
    private val hermes: HermesClient,
    private val transcriber: CommandTranscriber,
    private val phone: PhoneActions,
    private val permissions: PhonePermissions,
    private val wearable: Wearable,
    private val spokenAnswers: SpokenAnswerSettings,
    private val speaker: AnswerSpeaker,
) {
    suspend fun dispatch(route: Route, pcm: ShortArray) {
        val outcome = try {
            withContext(Dispatchers.IO) { carryOut(route, pcm) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CommandOutcome.Failed(e::class.java.simpleName)
        }
        when (outcome) {
            is CommandOutcome.Done -> Log.i(TAG, "Command done")
            is CommandOutcome.NotUnderstood -> Log.i(TAG, "Command not understood: ${outcome.reason}")
            is CommandOutcome.Failed -> Log.w(TAG, "Command failed: ${outcome.reason}")
        }
        wearable.signal(if (outcome is CommandOutcome.Done) WearableSignal.STOPPED else WearableSignal.FAILED)
        spokenReply(outcome, spokenAnswers.state.value)?.let(speaker::speak)
    }

    private suspend fun carryOut(route: Route, pcm: ShortArray): CommandOutcome = when (val destination = route.destination) {
        Destination.HomeAssistant -> {
            val config = settings.state.value
            val token = settings.homeAssistantToken()
            if (config.homeAssistantUrl.isBlank() || token == null) {
                CommandOutcome.Failed("not set up")
            } else {
                homeAssistant.run(config.homeAssistantUrl, token, config.pipeline, pcm)
            }
        }
        is Destination.Webhook -> {
            val text = transcriber.transcribe(pcm)
            if (text == null) {
                CommandOutcome.NotUnderstood("nothing transcribed")
            } else {
                webhooks.send(destination.url, settings.webhookSecret(route.id), text, route.wakeWord, Locale.getDefault().toLanguageTag())
            }
        }
        Destination.Phone -> {
            val text = transcriber.transcribe(pcm)
            val action = text?.let { PhoneCommandParser.parse(it, LocalDate.now()) }
            when {
                text == null -> CommandOutcome.NotUnderstood("nothing transcribed")
                action == null -> CommandOutcome.NotUnderstood("no phone action in it")
                !permissions.refresh() -> CommandOutcome.Failed("display over other apps not allowed")
                else -> phone.carryOut(action)
            }
        }
        Destination.Hermes -> {
            val address = settings.state.value.hermesUrl
            val key = settings.hermesKey()
            val text = if (address.isBlank() || key == null) null else transcriber.transcribe(pcm)
            when {
                address.isBlank() || key == null -> CommandOutcome.Failed("Hermes not set up")
                text == null -> CommandOutcome.NotUnderstood("nothing transcribed")
                else -> hermes.ask(address, key, text, Locale.getDefault().toLanguageTag())
            }
        }
    }

    private companion object {
        const val TAG = "Manent:Assist"
    }
}
