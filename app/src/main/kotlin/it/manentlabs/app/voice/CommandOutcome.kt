// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice

/** How a voice command ended. Reasons are for the log, never shown. */
sealed interface CommandOutcome {
    /** Carried out. [speech] is the destination's reply, if any; [answered] when it answers a question. */
    data class Done(val speech: String? = null, val answered: Boolean = false) : CommandOutcome

    /** Heard, but the destination did not know what to do with it. */
    data class NotUnderstood(val reason: String) : CommandOutcome

    /** The destination could not be reached or refused. */
    data class Failed(val reason: String) : CommandOutcome
}
