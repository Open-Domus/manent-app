// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable

import dev.omnesis.android.feature.assist.CommandFeedback
import dev.omnesis.android.feature.recording.WearableAudio

/**
 * A device the owner wears that streams speech to the phone: the recorder and
 * the voice-command listener only ever see this, never a specific device. An
 * Omi pendant is one ([OmiWearable]); another device is another adapter bound
 * in [WearableModule].
 */
interface Wearable {
    /** 20 ms Opus frames of 16 kHz mono speech, and whether the link holds. */
    val audio: WearableAudio

    /** Tell the wearer how a voice command went; a device that cannot signal ignores it. */
    fun signal(feedback: CommandFeedback)
}
