// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

/** The numbers the recording behaviour is built around, in one place. */
object RecordingLimits {
    /** How long a part runs when nothing cuts it sooner. */
    const val CHUNK_TARGET_MS = 60_000L

    /** The phone recorder's part size: 32 kbit/s for 60 s. */
    const val PHONE_CHUNK_BYTES = 240_000L

    /** Bytes per millisecond at the 32 kbit/s the app records at, for estimating lengths from sizes. */
    const val BYTES_PER_SECOND = 4_000L

    /** A recording stops on its own after this much recorded time. */
    const val MAX_SESSION_MS = 14_400_000L

    /** A recording whose wearable stays away this long is stopped and filed. */
    const val INPUT_LOSS_GIVE_UP_MS = 600_000L

    /** An all-day episode closes after this long without a part holding speech. */
    const val EPISODE_QUIET_MS = 90_000L

    /** A marked moment starts with up to this much of what was heard before the tap. */
    const val PRE_ROLL_MS = 30_000L
}
