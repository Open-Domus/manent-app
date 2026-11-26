// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.ui

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.manentlabs.app.gateway.Gateway
import it.manentlabs.app.gateway.TranscriberBlock
import it.manentlabs.app.voice.hermes.HermesClient
import it.manentlabs.app.voice.homeassistant.HomeAssistantAddress
import it.manentlabs.app.voice.homeassistant.HomeAssistantClient
import it.manentlabs.app.voice.phone.AppPick
import it.manentlabs.app.voice.phone.PhoneActionKind
import it.manentlabs.app.voice.phone.PhoneApps
import it.manentlabs.app.voice.phone.PhonePermissions
import it.manentlabs.app.voice.routes.Destination
import it.manentlabs.app.voice.routes.Route
import it.manentlabs.app.voice.routes.RouteStatus
import it.manentlabs.app.voice.routes.VoiceConfig
import it.manentlabs.app.voice.routes.VoiceSettings
import it.manentlabs.app.voice.transcribe.CommandTranscriber
import it.manentlabs.app.voice.transcribe.TranscriptionTiming
import it.manentlabs.app.voice.wakeword.ModelStore
import it.manentlabs.app.voice.webhook.WebhookUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject

/** The voice-commands card: routes, models, Home Assistant, Hermes Agent, phone actions and the switch. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VoiceCommandsViewModel @Inject constructor(
    private val settings: VoiceSettings,
    private val models: ModelStore,
    private val permissions: PhonePermissions,
    private val apps: PhoneApps,
    private val homeAssistant: HomeAssistantClient,
    private val hermes: HermesClient,
    gateway: Gateway,
    transcriber: CommandTranscriber,
) : ViewModel() {
    /** Moves on when the screen comes back, as permissions and files may have changed meanwhile. */
    private val resumed = MutableStateFlow(0)

    private data class Inputs(
        val config: VoiceConfig,
        val overlay: Boolean,
        val block: TranscriberBlock?,
        val timing: TranscriptionTiming?,
    )

    val card: StateFlow<VoiceCard?> = combine(
        combine(settings.state, models.version, permissions.overlay, resumed) { config, _, overlay, _ -> config to overlay },
        gateway.transcriberBlock,
        transcriber.lastTiming,
    ) { (config, overlay), block, timing -> Inputs(config, overlay, block, timing) }
        .mapLatest(::build)
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_DELAY_MS), null)

    private val _downloading = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = _downloading.asStateFlow()

    private val _test = MutableStateFlow<HomeAssistantTest>(HomeAssistantTest.Idle)
    val test: StateFlow<HomeAssistantTest> = _test.asStateFlow()

    private val _hermesTest = MutableStateFlow<HermesTest>(HermesTest.Idle)
    val hermesTest: StateFlow<HermesTest> = _hermesTest.asStateFlow()

    private val _error = MutableStateFlow<VoiceError?>(null)
    val error: StateFlow<VoiceError?> = _error.asStateFlow()

    fun onResume() {
        permissions.refresh()
        resumed.update { it + 1 }
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled && card.value?.anyActive != true) return
        settings.setEnabled(enabled)
    }

    fun setWord(routeId: String, wordId: String) = editRoutes { routes ->
        routes.map { if (it.id == routeId) it.copy(wakeWord = wordId) else it }
    }

    fun setDestination(routeId: String, choice: DestinationChoice) = editRoutes { routes ->
        routes.map { route ->
            if (route.id != routeId || choiceOf(route.destination) == choice) return@map route
            route.copy(
                destination = when (choice) {
                    DestinationChoice.HOME_ASSISTANT -> Destination.HomeAssistant
                    DestinationChoice.WEBHOOK -> Destination.Webhook("")
                    DestinationChoice.PHONE -> Destination.Phone
                    DestinationChoice.HERMES -> Destination.Hermes
                },
            )
        }
    }

    fun removeRoute(routeId: String) = editRoutes { routes -> routes.filter { it.id != routeId } }

    /** Adds a route for the first word not in use: to the phone if nothing goes there yet, else to a webhook. */
    fun addRoute() {
        val word = card.value?.unusedWords?.firstOrNull() ?: return
        editRoutes { routes ->
            val destination = if (routes.none { it.destination == Destination.Phone }) Destination.Phone else Destination.Webhook("")
            routes + Route(Route.newId(), word.id, destination)
        }
    }

    /** Saves a webhook's address and, if one was typed, its secret. False when the address is not usable. */
    fun saveWebhook(routeId: String, url: String, secret: String): Boolean {
        if (!WebhookUrl.isValid(url)) {
            _error.value = VoiceError.WebhookAddress
            return false
        }
        editRoutes { routes -> routes.map { if (it.id == routeId) it.copy(destination = Destination.Webhook(url.trim())) else it } }
        if (secret.isNotBlank()) settings.saveWebhookSecret(routeId, secret)
        return true
    }

    fun downloadModels() {
        val missing = card.value?.missing.orEmpty()
        if (missing.isEmpty() || _downloading.value) return
        _downloading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { models.download(missing) }
            } catch (e: IOException) {
                _error.value = VoiceError.Download(e.message ?: e::class.java.simpleName)
            } finally {
                _downloading.value = false
            }
        }
    }

    fun importModel(resolver: ContentResolver, uri: Uri) {
        _error.value = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val (name, size) = describe(resolver, uri)
                    models.import(name, size) {
                        try {
                            resolver.openInputStream(uri)
                        } catch (_: FileNotFoundException) {
                            null
                        }
                    }
                } catch (_: SecurityException) {
                    null
                }
            }
            when (result) {
                null -> _error.value = VoiceError.Unreadable
                is ModelStore.ImportResult.Refused -> _error.value = VoiceError.Import(result.problem)
                is ModelStore.ImportResult.Imported -> Unit
            }
        }
    }

    /** Saves the address (and the token, if typed) and tests them. */
    fun saveHomeAssistant(address: String, token: String) {
        if (HomeAssistantAddress.webSocketUrl(address) == null) {
            _error.value = VoiceError.HomeAssistantAddress
            return
        }
        _error.value = null
        _test.value = HomeAssistantTest.Running
        viewModelScope.launch {
            val check = withContext(Dispatchers.IO) {
                settings.saveHomeAssistant(address, token.takeIf { it.isNotBlank() })
                homeAssistant.test(address.trim(), settings.homeAssistantToken().orEmpty())
            }
            _test.value = HomeAssistantTest.Done(check)
        }
    }

    /** Saves Hermes Agent's address (and the API key, if typed) and tests them. */
    fun saveHermes(address: String, key: String) {
        if (HermesClient.endpoint(address, "models") == null) {
            _error.value = VoiceError.HermesAddress
            return
        }
        _error.value = null
        _hermesTest.value = HermesTest.Running
        viewModelScope.launch {
            val check = withContext(Dispatchers.IO) {
                settings.saveHermes(address, key.takeIf { it.isNotBlank() })
                hermes.test(address.trim(), settings.hermesKey().orEmpty())
            }
            _hermesTest.value = HermesTest.Done(check)
        }
    }

    fun setPipeline(id: String?) = settings.setPipeline(id)

    fun setPhoneApp(kind: PhoneActionKind, packageName: String) = settings.setPhoneApp(kind, packageName)

    /** Clears every voice-command setting and model. Spoken answers are a separate setting and stay. */
    fun forget() {
        _error.value = null
        _test.value = HomeAssistantTest.Idle
        _hermesTest.value = HermesTest.Idle
        viewModelScope.launch(Dispatchers.IO) {
            settings.forget()
            models.forget()
        }
    }

    private fun editRoutes(change: (List<Route>) -> List<Route>) {
        _error.value = null
        settings.saveRoutes(change(settings.state.value.routes))
    }

    private fun build(inputs: Inputs): VoiceCard {
        val config = inputs.config
        val words = models.words()
        val used = config.routes.map { it.wakeWord }.toSet()
        val rows = config.routes.map { route ->
            val word = words.firstOrNull { it.id == route.wakeWord }
            val installed = word != null && models.isInstalled(word)
            RouteRow(
                route = route,
                word = word,
                choices = words.filter { it.id == route.wakeWord || it.id !in used },
                problem = RouteStatus.problem(route, word, installed, config.homeAssistantReady, config.hermesReady),
                secretSaved = route.id in config.webhookSecrets,
            )
        }
        val toPhone = config.routes.any { it.destination == Destination.Phone }
        val needsText = config.routes.any { it.destination != Destination.HomeAssistant }
        return VoiceCard(
            config = config,
            rows = rows,
            unusedWords = words.filter { it.id !in used },
            missing = models.missing(rows.mapNotNull { it.word }),
            anyActive = rows.any { RouteStatus.isActive(it.problem, it.route, inputs.overlay) },
            overlayAllowed = inputs.overlay,
            callingAllowed = permissions.canReadContacts() && permissions.canCall(),
            phoneRows = if (toPhone) phoneRows(config) else null,
            transcription = when {
                !needsText -> null
                inputs.block != null -> TranscriptionLine.Unavailable(inputs.block)
                inputs.timing != null -> TranscriptionLine.Timing(inputs.timing)
                else -> TranscriptionLine.Ready
            },
            showForget = config.homeAssistantUrl.isNotBlank() || config.hasToken ||
                config.hermesUrl.isNotBlank() || config.hasHermesKey ||
                rows.any { it.word != null && models.isInstalled(it.word) } || config.routes != Route.DEFAULTS,
        )
    }

    private fun phoneRows(config: VoiceConfig): List<PhoneRow> = PhoneActionKind.entries.map { kind ->
        val candidates = apps.candidates(kind)
        val app = when (val pick = apps.pick(kind, config.phoneApps[kind])) {
            is AppPick.Use -> candidates.firstOrNull { it.packageName == pick.packageName }
            AppPick.NoApp, AppPick.MustChoose -> null
        }
        PhoneRow(kind, candidates, app)
    }

    private fun describe(resolver: ContentResolver, uri: Uri): Pair<String?, Long?> {
        val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return resolver.query(uri, columns, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null to null
            val name = if (cursor.isNull(0)) null else cursor.getString(0)
            val size = if (cursor.isNull(1)) null else cursor.getLong(1)
            name to size
        } ?: (uri.lastPathSegment to null)
    }

    private companion object {
        const val STOP_DELAY_MS = 5_000L
    }
}
