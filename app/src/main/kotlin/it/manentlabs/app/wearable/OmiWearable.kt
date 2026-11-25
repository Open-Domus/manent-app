// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable

import it.manentlabs.app.feature.assist.CommandFeedback
import it.manentlabs.app.feature.omi.Haptic
import it.manentlabs.app.feature.omi.OmiLink
import it.manentlabs.app.feature.recording.WearableAudio

/** An Omi pendant: its link streams the audio, its motor signals with the recorder's own patterns. */
class OmiWearable(private val link: OmiLink) : Wearable {
    override val audio: WearableAudio = link

    override fun signal(feedback: CommandFeedback) = link.vibrate(haptic(feedback))

    companion object {
        /** One short pulse to say the command, two once it is done, one long one when it failed. */
        fun haptic(feedback: CommandFeedback): Haptic = when (feedback) {
            CommandFeedback.LISTENING -> Haptic.STARTED
            CommandFeedback.DONE -> Haptic.STOPPED
            CommandFeedback.FAILED -> Haptic.FAILED
        }
    }
}
