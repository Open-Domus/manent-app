// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Lorenzo Farnararo

package it.manentlabs.app.feature.assist.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.manentlabs.app.feature.assist.AssistOutcome
import it.manentlabs.app.feature.assist.AssistPipelineClient
import it.manentlabs.app.feature.assist.AssistRuntime
import it.manentlabs.app.feature.assist.AssistSettings
import it.manentlabs.app.feature.assist.CommandRouter
import it.manentlabs.app.feature.assist.CommandTranscribers
import it.manentlabs.app.feature.assist.CommandTranscription
import it.manentlabs.app.feature.assist.OpenWakeWordDetector
import it.manentlabs.app.feature.assist.PhoneActionCatalog
import it.manentlabs.app.feature.assist.PhoneActionRunner
import it.manentlabs.app.feature.assist.VoiceCommands
import it.manentlabs.app.feature.assist.WakeWordModelStore
import it.manentlabs.app.feature.assist.WebhookClient
import it.manentlabs.app.feature.assist.WebhookCommand
import it.manentlabs.app.feature.recording.MediaCodecOpusDecoder
import it.manentlabs.app.feature.recording.SileroSpeechDetector
import it.manentlabs.app.pairing.KeystorePrefsCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.io.File
import java.time.Instant
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Voice commands. The app provides [CommandTranscribers]: the gateway's and
 * the phone's transcribers live in it, beside the session and the recognizer.
 */
@Module
@InstallIn(SingletonComponent::class)
object AssistModule {
    /** Home Assistant and webhooks are the user's own servers, not the gateway: no gateway pinning, the system's trust. */
    @Provides
    @Singleton
    fun provideAssistHttp(): AssistHttp = AssistHttp(
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .build(),
    )

    @Provides
    @Singleton
    fun provideSettings(@ApplicationContext context: Context): AssistSettings =
        AssistSettings(context.getSharedPreferences(AssistSettings.PREFS_NAME, Context.MODE_PRIVATE), KeystorePrefsCipher())

    @Provides
    @Singleton
    fun provideClient(http: AssistHttp): AssistPipelineClient =
        // A pipeline whose agent is an LLM can take well over 20 s to answer a question.
        AssistPipelineClient(http.client, timeoutMs = 60_000)

    @Provides
    @Singleton
    fun provideWebhookClient(http: AssistHttp): WebhookClient = WebhookClient(http.client)

    @Provides
    @Singleton
    fun providePhoneCatalog(@ApplicationContext context: Context, settings: AssistSettings): PhoneActionCatalog =
        PhoneActionCatalog(context, preferred = { settings.state.value.phoneApps[it] })

    @Provides
    @Singleton
    fun providePhoneActions(@ApplicationContext context: Context, catalog: PhoneActionCatalog): PhoneActionRunner =
        PhoneActionRunner(context, catalog)

    @Provides
    @Singleton
    fun provideModels(@ApplicationContext context: Context, http: AssistHttp): WakeWordModelStore =
        WakeWordModelStore(File(context.noBackupFilesDir, "wakeword"), http.client)

    @Provides
    @Singleton
    fun provideTranscription(
        @ApplicationContext context: Context,
        settings: AssistSettings,
        transcribers: CommandTranscribers,
    ): CommandTranscription = CommandTranscription(
        transcribers = transcribers,
        kind = { settings.state.value.transcriber },
        directory = File(context.cacheDir, "voice-commands"),
    )

    @Provides
    @Singleton
    fun provideRuntime(
        @ApplicationContext context: Context,
        settings: AssistSettings,
        client: AssistPipelineClient,
        webhooks: WebhookClient,
        phone: PhoneActionRunner,
        transcription: CommandTranscription,
        models: WakeWordModelStore,
    ): AssistRuntime {
        val router = CommandRouter(
            homeAssistant = { pcm -> settings.config()?.let { client.run(it, pcm) } ?: AssistOutcome.Failed("not set up") },
            transcribe = transcription::transcribe,
            webhook = { route, url, text ->
                val command = WebhookCommand(text, route.wakeWord, Locale.getDefault().toLanguageTag(), Instant.now().toString())
                webhooks.send(url, settings.webhookSecret(route.id), command)
            },
            phone = phone::run,
        )
        return AssistRuntime(
            settings = settings,
            models = models,
            commands = VoiceCommands(
                decoders = ::MediaCodecOpusDecoder,
                wakeDetectors = { words -> OpenWakeWordDetector(words.size) { models.load(words) } },
                speechDetectors = { SileroSpeechDetector { context.assets.open(SileroSpeechDetector.MODEL_ASSET).use { it.readBytes() } } },
                send = router::send,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                workers = { Executors.newSingleThreadExecutor { Thread(it, "manent-voice-commands") } },
            ),
            phoneAllowed = phone.allowed,
        )
    }
}

/** The HTTP client for Home Assistant, webhooks and the model downloads, kept apart from the gateway's pinned client. */
class AssistHttp(val client: OkHttpClient)
