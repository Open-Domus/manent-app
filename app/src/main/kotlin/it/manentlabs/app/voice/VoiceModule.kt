// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.manentlabs.app.voice.hermes.HermesClient
import it.manentlabs.app.voice.homeassistant.HomeAssistantClient
import it.manentlabs.app.voice.wakeword.ModelStore
import it.manentlabs.app.voice.webhook.WebhookSender
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** The HTTP client for Home Assistant, Hermes Agent, webhooks and model downloads: the system's CAs, not the gateway's pinning. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VoiceHttp

@Module
@InstallIn(SingletonComponent::class)
object VoiceModule {
    @Provides
    @Singleton
    @VoiceHttp
    fun http(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    /** Kept out of backups: the models can always be fetched again. */
    @Provides
    @Singleton
    fun models(@ApplicationContext context: Context, @VoiceHttp http: OkHttpClient): ModelStore =
        ModelStore(File(context.noBackupFilesDir, "wakeword"), http)

    @Provides
    @Singleton
    fun homeAssistant(@VoiceHttp http: OkHttpClient): HomeAssistantClient = HomeAssistantClient(http)

    @Provides
    @Singleton
    fun webhooks(@VoiceHttp http: OkHttpClient): WebhookSender = WebhookSender(http)

    @Provides
    @Singleton
    fun hermes(@VoiceHttp http: OkHttpClient): HermesClient = HermesClient(http)
}
