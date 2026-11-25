// SPDX-License-Identifier: MIT

package it.manentlabs.app.audio

import kotlinx.coroutines.flow.StateFlow

/**
 * Lets several listeners hear one wearable at once: the recorder and the
 * wake-word listener each [open] a stream that behaves like the device itself.
 * The device streams while at least one of them does.
 *
 * Frames go to every listener on the device's own callback, so listeners hand
 * them off instead of working on them there.
 */
class AudioHub(private val device: WearableAudio) {
    private val lock = Any()
    @Volatile private var listeners: List<Stream> = emptyList()

    private val fanOut = object : AudioSink {
        override fun onFrame(opus: ByteArray, receivedAtMs: Long) {
            for (stream in listeners) stream.sink?.onFrame(opus, receivedAtMs)
        }

        override fun onLinkLost() {
            val lost = synchronized(lock) {
                val all = listeners
                listeners = emptyList()
                all
            }
            for (stream in lost) stream.detach()?.onLinkLost()
        }
    }

    /** A stream of its own over the shared device. */
    fun open(): WearableAudio = Stream()

    private inner class Stream : WearableAudio {
        @Volatile var sink: AudioSink? = null

        override val name: String get() = device.name
        override val ready: StateFlow<Boolean> get() = device.ready

        override fun start(sink: AudioSink): Boolean = synchronized(lock) {
            if (this in listeners) {
                this.sink = sink
                return true
            }
            if (listeners.isEmpty() && !device.start(fanOut)) return false
            this.sink = sink
            listeners = listeners + this
            true
        }

        override fun stop() = synchronized(lock) {
            if (this !in listeners) return
            listeners = listeners - this
            sink = null
            if (listeners.isEmpty()) device.stop()
        }

        fun detach(): AudioSink? {
            val s = sink
            sink = null
            return s
        }
    }
}
