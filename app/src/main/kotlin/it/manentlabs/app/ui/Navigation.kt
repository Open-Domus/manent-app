// SPDX-License-Identifier: MIT

package it.manentlabs.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import it.manentlabs.app.gateway.ui.PairingScreen
import it.manentlabs.app.recording.ui.RecordingsScreen
import it.manentlabs.app.voice.ui.VoiceCommandsScreen

/** The app's screens. Each area adds its routes here. */
@Composable
fun ManentNavigation() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onConnectGateway = { nav.navigate(Routes.PAIR_GATEWAY) },
                onRecordings = { nav.navigate(Routes.RECORDINGS) },
                onVoiceCommands = { nav.navigate(Routes.VOICE_COMMANDS) },
            )
        }
        composable(Routes.VOICE_COMMANDS) { VoiceCommandsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.PAIR_GATEWAY) { PairingScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.RECORDINGS) {
            RecordingsScreen(
                onBack = { nav.popBackStack() },
                onManageWearable = { nav.popBackStack(Routes.HOME, inclusive = false) },
                onConnectGateway = { nav.navigate(Routes.PAIR_GATEWAY) },
            )
        }
    }
}

object Routes {
    const val HOME = "home"
    const val PAIR_GATEWAY = "gateway/pair"
    const val RECORDINGS = "recordings"
    const val VOICE_COMMANDS = "voice"
}
