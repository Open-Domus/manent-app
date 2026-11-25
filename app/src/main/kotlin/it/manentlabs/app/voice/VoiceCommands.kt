// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice

import android.util.Log
import it.manentlabs.app.audio.AudioHub
import it.manentlabs.app.recording.VoiceDetectors
import it.manentlabs.app.voice.phone.PhonePermissions
import it.manentlabs.app.voice.routes.RouteStatus
import it.manentlabs.app.voice.routes.VoiceConfig
import it.manentlabs.app.voice.routes.VoiceSettings
import it.manentlabs.app.voice.wakeword.ModelStore
import it.manentlabs.app.voice.wakeword.WakeWordCatalogue
import it.manentlabs.app.wearable.Wearable
import it.manentlabs.app.wearable.WearableSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps wake-word listening in step with everything it depends on: listens
 * while the wearable is ready, voice commands are on, and at least one route
 * is active. Any change to the wearable's readiness, the settings, the
 * installed models or the overlay permission re-plans; the same plan leaves
 * the running listener alone, a different one starts over.
 *
 * Lives as long as the process, which the wearable's foreground service keeps up.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class VoiceCommands @Inject constructor(
    private val hub: AudioHub,
    private val wearable: Wearable,
    private val settings: VoiceSettings,
    private val models: ModelStore,
    private val permissions: PhonePermissions,
    private val detectors: VoiceDetectors,
    private val dispatcher: CommandDispatcher,
) {
    private val serial = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + serial)
    private val linkLosses = MutableStateFlow(0)
    private var running: Pair<ListenPlan, CommandListener>? = null

    fun start() {
        val stream = hub.open()
        scope.launch {
            combine(stream.ready, settings.state, models.version, permissions.overlay, linkLosses) { ready, config, _, overlay, _ ->
                if (ready) plan(config, overlay) else null
            }.collect(::apply)
        }
    }

    /** The words to listen for now, or null to stay quiet. */
    private fun plan(config: VoiceConfig, overlayAllowed: Boolean): ListenPlan? {
        if (!config.enabled) return null
        val armed = config.routes.mapNotNull { route ->
            val word = models.word(route.wakeWord)
            val installed = word != null && models.isInstalled(word)
            val problem = RouteStatus.problem(route, word, installed, config.homeAssistantReady)
            if (word == null || !RouteStatus.isActive(problem, route, overlayAllowed)) return@mapNotNull null
            ListenPlan.Armed(route.id, word.id, models.classifierFile(word))
        }
        if (armed.isEmpty()) return null
        return ListenPlan(
            armed,
            models.sharedFile(WakeWordCatalogue.MEL_FILE),
            models.sharedFile(WakeWordCatalogue.EMBEDDING_FILE),
        )
    }

    private fun apply(plan: ListenPlan?) {
        if (plan == running?.first) return
        running?.second?.stop()
        running = null
        if (plan == null) return
        val relay = Relay()
        val listener = CommandListener(hub.open(), plan, detectors::create, relay)
        relay.owner = listener
        if (listener.start()) {
            running = plan to listener
            Log.i(TAG, "Listening for ${plan.words.size} wake word(s)")
        } else {
            listener.stop()
        }
    }

    /** Passes one listener's events on; [owner] is that listener, so a late event from an old one is ignored. */
    private inner class Relay : CommandListener.Events {
        var owner: CommandListener? = null

        override fun onHeard() = wearable.signal(WearableSignal.STARTED)

        override fun onCommand(routeId: String, pcm: ShortArray) {
            scope.launch(Dispatchers.IO) {
                val route = settings.state.value.routes.firstOrNull { it.id == routeId } ?: return@launch
                dispatcher.dispatch(route, pcm)
            }
        }

        override fun onLinkLost() {
            scope.launch {
                if (running?.second !== owner) return@launch
                running?.second?.stop()
                running = null
                linkLosses.update { it + 1 }
            }
        }
    }

    private companion object {
        const val TAG = "Manent:Assist"
    }
}
