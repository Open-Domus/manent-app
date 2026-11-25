// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import it.manentlabs.app.audio.AudioHub
import it.manentlabs.app.wearable.omi.OmiLink
import javax.inject.Singleton

/** The one place that decides which hardware the app's wearable is. */
@Module
@InstallIn(SingletonComponent::class)
object WearableModule {
    @Provides
    @Singleton
    fun wearable(link: OmiLink): Wearable = OmiWearable(link)

    /** Shared by the recorder and the voice-command listener, so both can hear the wearable at once. */
    @Provides
    @Singleton
    fun audioHub(wearable: Wearable): AudioHub = AudioHub(wearable.audio)
}
