// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Lorenzo Farnararo

package it.manentlabs.app.feature.recording.di

import android.content.Context
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.manentlabs.app.feature.recording.ChunkSpeechScanner
import it.manentlabs.app.feature.recording.MarkedMomentSink
import it.manentlabs.app.feature.recording.RecordingAvailabilityMemory
import it.manentlabs.app.feature.recording.RecordingController
import it.manentlabs.app.feature.recording.RecordingFiles
import it.manentlabs.app.feature.recording.RecordingOutbox
import it.manentlabs.app.feature.recording.RecordingSettings
import it.manentlabs.app.feature.recording.RecordingUploadScheduler
import it.manentlabs.app.feature.recording.SileroSpeechDetector
import it.manentlabs.app.feature.recording.SqliteRecordingOutbox
import it.manentlabs.app.feature.recording.WearableAudio
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RecordingModule {
    @Provides
    @Singleton
    fun provideRecordingOutbox(@ApplicationContext context: Context): RecordingOutbox =
        SqliteRecordingOutbox(context)

    @Provides
    @Singleton
    fun provideRecordingFiles(@ApplicationContext context: Context): RecordingFiles =
        RecordingFiles(File(context.noBackupFilesDir, "recordings"))

    @Provides
    @Singleton
    fun provideRecordingSettings(@ApplicationContext context: Context): RecordingSettings =
        RecordingSettings(context.getSharedPreferences(RecordingSettings.PREFS_NAME, Context.MODE_PRIVATE))

    @Provides
    @Singleton
    fun provideRecordingAvailabilityMemory(@ApplicationContext context: Context): RecordingAvailabilityMemory =
        RecordingAvailabilityMemory(
            context.getSharedPreferences(RecordingAvailabilityMemory.PREFS_NAME, Context.MODE_PRIVATE),
        )

    @Provides
    @Singleton
    fun provideRecordingController(
        @ApplicationContext context: Context,
        outbox: RecordingOutbox,
        files: RecordingFiles,
        settings: RecordingSettings,
    ): RecordingController {
        val detector = SileroSpeechDetector { context.assets.open(SileroSpeechDetector.MODEL_ASSET).use { it.readBytes() } }
        return RecordingController(
            outbox = outbox,
            files = files,
            onWorkQueued = { RecordingUploadScheduler.enqueue(context, settings.wifiOnly) },
            speechCheck = ChunkSpeechScanner(detector)::scan,
        )
    }
}

/** A wearable input is bound by the module that links one; without it, recording uses the phone only. */
@Module
@InstallIn(SingletonComponent::class)
interface WearableAudioModule {
    @BindsOptionalOf
    fun optionalWearableAudio(): WearableAudio

    /** Bound by the app, which saves a marked moment as a note; without it a clip is dropped. */
    @BindsOptionalOf
    fun optionalMarkedMomentSink(): MarkedMomentSink
}
