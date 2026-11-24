// SPDX-License-Identifier: MIT

package it.manentlabs.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import it.manentlabs.app.wearable.VoiceCommandsCoordinator
import javax.inject.Inject

@HiltAndroidApp
class ManentApp : Application() {
    @Inject lateinit var voiceCommands: VoiceCommandsCoordinator

    override fun onCreate() {
        super.onCreate()
        voiceCommands.start()
    }
}
