// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.ui

import it.manentlabs.app.gateway.TranscriberBlock
import it.manentlabs.app.voice.homeassistant.ConnectionCheck
import it.manentlabs.app.voice.phone.PhoneActionKind
import it.manentlabs.app.voice.phone.PhoneApp
import it.manentlabs.app.voice.routes.Route
import it.manentlabs.app.voice.routes.RouteProblem
import it.manentlabs.app.voice.routes.VoiceConfig
import it.manentlabs.app.voice.transcribe.TranscriptionTiming
import it.manentlabs.app.voice.wakeword.ModelStore
import it.manentlabs.app.voice.wakeword.WakeWord

/** One wake word on the card: its route, the word if still known, the words it may switch to, and what blocks it. */
data class RouteRow(
    val route: Route,
    val word: WakeWord?,
    val choices: List<WakeWord>,
    val problem: RouteProblem?,
    val secretSaved: Boolean,
)

/** One kind of phone action: the apps that can do it and the one that will, if decided. */
data class PhoneRow(val kind: PhoneActionKind, val apps: List<PhoneApp>, val app: PhoneApp?)

/** The line under the transcription note. */
sealed interface TranscriptionLine {
    data class Unavailable(val block: TranscriberBlock) : TranscriptionLine
    data class Timing(val timing: TranscriptionTiming) : TranscriptionLine
    data object Ready : TranscriptionLine
}

/** Everything the voice-commands card shows that comes from the phone's state. */
data class VoiceCard(
    val config: VoiceConfig,
    val rows: List<RouteRow>,
    val unusedWords: List<WakeWord>,
    /** Catalogue words of the routes whose models are not here yet. */
    val missing: List<WakeWord>,
    val anyActive: Boolean,
    val overlayAllowed: Boolean,
    val callingAllowed: Boolean,
    /** Null when no route goes to the phone. */
    val phoneRows: List<PhoneRow>?,
    /** Null when no route needs a transcript. */
    val transcription: TranscriptionLine?,
    val showForget: Boolean,
)

/** Something the owner did that did not work, shown under the card. */
sealed interface VoiceError {
    data class Download(val message: String) : VoiceError
    data class Import(val problem: ModelStore.ImportProblem) : VoiceError
    data object Unreadable : VoiceError
    data object HomeAssistantAddress : VoiceError
    data object WebhookAddress : VoiceError
}

/** The Home Assistant test, while it runs and once it answered. */
sealed interface HomeAssistantTest {
    data object Idle : HomeAssistantTest
    data object Running : HomeAssistantTest
    data class Done(val check: ConnectionCheck) : HomeAssistantTest
}

/** The destinations a route can be switched to. */
enum class DestinationChoice { HOME_ASSISTANT, WEBHOOK, PHONE }
