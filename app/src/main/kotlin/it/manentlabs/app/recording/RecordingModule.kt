// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import it.manentlabs.app.wearable.RecorderControl

/** The wearable side drives the recorder through [RecorderControl]. */
@Module
@InstallIn(SingletonComponent::class)
interface RecordingModule {
    @Binds
    fun recorderControl(impl: Recorder): RecorderControl
}
