// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable.omi

import java.io.ByteArrayOutputStream

/**
 * Rebuilds whole Opus packets from the pendant's audio notifications.
 *
 * The firmware splits each frame over notifications shaped
 * `[counter lo][counter hi][fragment index][payload…]`; a frame ends where the
 * next fragment 0 starts. A frame with any gap in the counter or the fragment
 * index is dropped rather than handed to the decoder half-filled, and counted
 * in [framesLost] so the details panel can show how lossy the link is.
 *
 * Not thread-safe: the link feeds it from one callback thread.
 */
class FrameAssembler {
    private var lastCounter = NONE
    private var lastIndex = NONE
    private var frame: ByteArrayOutputStream? = null

    var framesLost = 0
        private set

    fun reset() {
        lastCounter = NONE
        lastIndex = NONE
        frame = null
        framesLost = 0
    }

    /** Takes one notification; returns the frame it completed, if any. */
    fun accept(packet: ByteArray): ByteArray? {
        if (packet.size < HEADER) return null
        val counter = (packet[0].toInt() and 0xff) or ((packet[1].toInt() and 0xff) shl 8)
        val index = packet[2].toInt() and 0xff
        val inOrder = lastCounter != NONE && counter == (lastCounter + 1) and 0xffff
        val previousIndex = lastIndex
        lastCounter = counter
        lastIndex = index

        val current = frame
        if (index == 0) {
            var done: ByteArray? = null
            if (current != null) {
                if (inOrder) done = current.toByteArray().takeIf { it.isNotEmpty() } else framesLost++
            }
            frame = ByteArrayOutputStream().apply { write(packet, HEADER, packet.size - HEADER) }
            return done
        }
        if (current == null) return null
        if (!inOrder || index != previousIndex + 1) {
            frame = null
            framesLost++
            return null
        }
        current.write(packet, HEADER, packet.size - HEADER)
        return null
    }

    private companion object {
        const val HEADER = 3
        const val NONE = -1
    }
}
