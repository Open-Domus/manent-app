// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import it.manentlabs.app.R
import it.manentlabs.app.gateway.TranscriberBlock
import it.manentlabs.app.voice.hermes.HermesCheck
import it.manentlabs.app.voice.homeassistant.ConnectionCheck
import it.manentlabs.app.voice.phone.PhoneActionKind
import it.manentlabs.app.voice.routes.RouteProblem
import it.manentlabs.app.voice.wakeword.ModelOrigin
import it.manentlabs.app.voice.wakeword.ModelStore
import it.manentlabs.app.voice.wakeword.WakeWord

/** The card's wording for the voice-command model's states. */
internal object VoiceText {
    @Composable
    fun word(word: WakeWord): String =
        stringResource(if (word.imported) R.string.voice_word_imported else R.string.voice_word, word.phrase)

    @Composable
    fun origin(origin: ModelOrigin): String = stringResource(
        when (origin) {
            ModelOrigin.OPEN_WAKE_WORD -> R.string.voice_origin_openwakeword
            ModelOrigin.COMMUNITY -> R.string.voice_origin_community
            ModelOrigin.IMPORTED -> R.string.voice_origin_imported
        },
    )

    @Composable
    fun destination(choice: DestinationChoice): String = when (choice) {
        DestinationChoice.HOME_ASSISTANT -> stringResource(R.string.voice_home_assistant)
        DestinationChoice.WEBHOOK -> stringResource(R.string.voice_destination_webhook)
        DestinationChoice.PHONE -> stringResource(R.string.voice_destination_phone)
        DestinationChoice.HERMES -> stringResource(R.string.voice_hermes)
    }

    @Composable
    fun problem(problem: RouteProblem): String = stringResource(
        when (problem) {
            RouteProblem.WORD_GONE -> R.string.voice_problem_word_gone
            RouteProblem.IMPORT_MISSING -> R.string.voice_problem_import_missing
            RouteProblem.DOWNLOAD -> R.string.voice_problem_download
            RouteProblem.HOME_ASSISTANT -> R.string.voice_problem_home_assistant
            RouteProblem.WEBHOOK -> R.string.voice_problem_webhook
            RouteProblem.HERMES -> R.string.voice_problem_hermes
        },
    )

    @Composable
    fun action(kind: PhoneActionKind): String = stringResource(
        when (kind) {
            PhoneActionKind.TIMER -> R.string.voice_action_timer
            PhoneActionKind.ALARM -> R.string.voice_action_alarm
            PhoneActionKind.NAVIGATE -> R.string.voice_action_navigate
            PhoneActionKind.MUSIC -> R.string.voice_action_music
            PhoneActionKind.OPEN_APP -> R.string.voice_action_open_app
            PhoneActionKind.CALL -> R.string.voice_action_call
            PhoneActionKind.CALENDAR -> R.string.voice_action_calendar
            PhoneActionKind.SHARE -> R.string.voice_action_share
        },
    )

    @Composable
    fun example(kind: PhoneActionKind): String = stringResource(
        when (kind) {
            PhoneActionKind.TIMER -> R.string.voice_example_timer
            PhoneActionKind.ALARM -> R.string.voice_example_alarm
            PhoneActionKind.NAVIGATE -> R.string.voice_example_navigate
            PhoneActionKind.MUSIC -> R.string.voice_example_music
            PhoneActionKind.OPEN_APP -> R.string.voice_example_open_app
            PhoneActionKind.CALL -> R.string.voice_example_call
            PhoneActionKind.CALENDAR -> R.string.voice_example_calendar
            PhoneActionKind.SHARE -> R.string.voice_example_share
        },
    )

    @Composable
    fun check(check: ConnectionCheck): String = when (check) {
        is ConnectionCheck.Ok -> check.version?.let { stringResource(R.string.voice_check_ok_version, it) }
            ?: stringResource(R.string.voice_check_ok)
        ConnectionCheck.BadToken -> stringResource(R.string.voice_check_bad_token)
        is ConnectionCheck.Unreachable -> stringResource(R.string.voice_check_unreachable, check.reason)
    }

    @Composable
    fun hermesCheck(check: HermesCheck): String = when (check) {
        is HermesCheck.Ok -> check.model?.let { stringResource(R.string.voice_hermes_check_ok_model, it) }
            ?: stringResource(R.string.voice_hermes_check_ok)
        HermesCheck.BadKey -> stringResource(R.string.voice_hermes_check_bad_key)
        is HermesCheck.Unreachable -> stringResource(R.string.voice_hermes_check_unreachable, check.reason)
    }

    @Composable
    fun block(block: TranscriberBlock): String = stringResource(
        when (block) {
            TranscriberBlock.NOT_PAIRED -> R.string.app_transcriber_not_paired
            TranscriberBlock.DICTATION_OFF -> R.string.app_transcriber_dictation_off
        },
    )

    @Composable
    fun seconds(ms: Long): String = stringResource(R.string.voice_seconds, ms / 1000.0)

    @Composable
    fun error(error: VoiceError): String = when (error) {
        is VoiceError.Download -> stringResource(R.string.voice_error_download, error.message)
        is VoiceError.Import -> stringResource(R.string.voice_error_import, importProblem(error.problem))
        VoiceError.Unreadable -> stringResource(R.string.voice_error_unreadable)
        VoiceError.HomeAssistantAddress -> stringResource(R.string.voice_error_home_assistant_address)
        VoiceError.WebhookAddress -> stringResource(R.string.voice_error_webhook_address)
        VoiceError.HermesAddress -> stringResource(R.string.voice_error_hermes_address)
    }

    @Composable
    private fun importProblem(problem: ModelStore.ImportProblem): String = when (problem) {
        ModelStore.ImportProblem.NotOnnx -> stringResource(R.string.voice_import_not_onnx)
        ModelStore.ImportProblem.CannotOpen -> stringResource(R.string.voice_import_cannot_open)
        ModelStore.ImportProblem.NoName -> stringResource(R.string.voice_import_no_name)
        ModelStore.ImportProblem.TooLarge -> stringResource(R.string.voice_import_too_large)
        ModelStore.ImportProblem.NotClassifier -> stringResource(R.string.voice_import_not_classifier)
        is ModelStore.ImportProblem.Exists -> stringResource(R.string.voice_import_exists, problem.phrase)
        is ModelStore.ImportProblem.Failed -> problem.message
    }
}
