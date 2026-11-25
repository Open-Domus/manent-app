// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import it.manentlabs.app.feature.omi.OmiLink
import it.manentlabs.app.feature.recording.SharedWearableAudio
import it.manentlabs.app.feature.recording.WearableAudio
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WearableModule {
    /** The one place that names the hardware: an Omi pendant today. */
    @Provides
    @Singleton
    fun provideWearable(link: OmiLink): Wearable = OmiWearable(link)

    /** The wearable's audio, heard by the recorder and the voice-command listener at once. */
    @Provides
    @Singleton
    fun provideSharedAudio(wearable: Wearable): SharedWearableAudio = SharedWearableAudio(wearable.audio)

    /** The recorder's wearable input, through its own view of the shared audio. */
    @Provides
    @Singleton
    fun provideWearableAudio(shared: SharedWearableAudio): WearableAudio = shared.view()
}
