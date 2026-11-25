// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable

import it.manentlabs.app.audio.WearableAudio
import it.manentlabs.app.wearable.omi.HapticPattern
import it.manentlabs.app.wearable.omi.OmiLink

/** The Omi pendant as the app's wearable: its audio, and its motor for feedback. */
class OmiWearable(private val link: OmiLink) : Wearable {
    override val audio: WearableAudio get() = link

    override fun signal(signal: WearableSignal) = link.play(HapticPattern.of(signal))
}
