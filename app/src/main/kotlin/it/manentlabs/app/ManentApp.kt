// SPDX-License-Identifier: MIT

package it.manentlabs.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import it.manentlabs.app.voice.VoiceCommands
import javax.inject.Inject

@HiltAndroidApp
class ManentApp : Application() {
    @Inject lateinit var voiceCommands: VoiceCommands

    override fun onCreate() {
        super.onCreate()
        // Listens for wake words whenever the pendant streams and voice commands are set up.
        voiceCommands.start()
    }
}
