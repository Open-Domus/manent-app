// SPDX-License-Identifier: MIT

package it.manentlabs.app.audio

import kotlinx.coroutines.flow.StateFlow

/**
 * Speech a worn device streams: 20 ms Opus frames of 16 kHz mono audio.
 *
 * Only one sink receives the frames at a time; [AudioHub] gives each part of
 * the app a stream of its own on top of the one device.
 */
interface WearableAudio {
    /** What to call the device in the app's text, such as "Omi". */
    val name: String

    /** Whether the link is up and the device can stream now. */
    val ready: StateFlow<Boolean>

    /** Starts sending frames to [sink]; false when the device cannot stream now. */
    fun start(sink: AudioSink): Boolean

    /** Stops sending frames. Safe to call when nothing streams. */
    fun stop()
}

interface AudioSink {
    /** One whole Opus packet, with the wall-clock time it arrived in ms. Must return quickly. */
    fun onFrame(opus: ByteArray, receivedAtMs: Long)

    /** The link dropped mid-stream. No more frames come until [WearableAudio.start] again. */
    fun onLinkLost()
}
