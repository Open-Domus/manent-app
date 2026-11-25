// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable

import it.manentlabs.app.audio.WearableAudio

/**
 * A device worn by the owner that streams speech to the phone. The recorder
 * and the voice commands only ever see this; the one place that names the
 * hardware is [WearableModule].
 */
interface Wearable {
    val audio: WearableAudio

    /** Tells the wearer something happened; a device that cannot signal ignores it. */
    fun signal(signal: WearableSignal)
}

/** What the wearer feels. Each device picks its own pattern for each. */
enum class WearableSignal {
    /** A recording started, or a wake word was heard: go on. */
    STARTED,

    /** A recording stopped, or a command was carried out. */
    STOPPED,

    /** A moment was marked. */
    MARKED,

    /** Something did not work. */
    FAILED,
}
