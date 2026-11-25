// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.phone

/** Which app carries out a kind of action, decided without asking: a command arrives with nobody at the screen. */
sealed interface AppPick {
    data class Use(val packageName: String) : AppPick

    /** No app on the phone takes it. */
    data object NoApp : AppPick

    /** Several apps take it and none is set: the owner must choose one in the settings. */
    data object MustChoose : AppPick

    companion object {
        /**
         * The owner's [chosen] app if it is still among [candidates]; else the
         * only candidate; else the [systemDefault] Android would open without a
         * chooser, if it is a candidate.
         */
        fun of(chosen: String?, candidates: List<String>, systemDefault: String?): AppPick = when {
            candidates.isEmpty() -> NoApp
            chosen != null && chosen in candidates -> Use(chosen)
            candidates.size == 1 -> Use(candidates.single())
            systemDefault != null && systemDefault in candidates -> Use(systemDefault)
            else -> MustChoose
        }
    }
}
