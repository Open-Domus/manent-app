// SPDX-License-Identifier: MIT

package it.manentlabs.app.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import it.manentlabs.app.omnesis.GatewaySessions
import it.manentlabs.app.speech.AnswerSpeaker
import it.manentlabs.app.speech.SpokenAnswerSettings
import it.manentlabs.app.designsystem.theme.OmnesisTheme
import it.manentlabs.app.feature.omi.ui.OmiRecordingRow
import it.manentlabs.app.feature.recording.ui.RecordingScreen
import it.manentlabs.app.feature.recording.ui.RecordingSpeakersScreen
import it.manentlabs.app.feature.recording.ui.RecordingSpeakersViewModel
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var sessions: GatewaySessions
    @Inject lateinit var spokenAnswers: SpokenAnswerSettings
    @Inject lateinit var speaker: AnswerSpeaker

    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // The foreground services still run without it; their notifications are just hidden.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            OmnesisTheme {
                ManentNav(sessions, spokenAnswers, speaker)
            }
        }
    }
}

@Composable
private fun ManentNav(sessions: GatewaySessions, spokenAnswers: SpokenAnswerSettings, speaker: AnswerSpeaker) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                sessions,
                spokenAnswers,
                speaker,
                onOpenRecordings = { nav.navigate("recordings") },
                onConnectOmnesis = { nav.navigate("omnesis/pair") },
            )
        }
        composable("omnesis/pair") {
            PairingScreen(sessions, onPaired = { nav.popBackStack() }, onBack = { nav.popBackStack() })
        }
        composable("recordings") {
            RecordingScreen(
                onOpenMenu = { nav.popBackStack() },
                wearableCard = { OmiRecordingRow(onOpenSettings = { nav.popBackStack("home", inclusive = false) }) },
                onOpenSpeakers = { id, title ->
                    nav.navigate("recordings/${Uri.encode(id)}/speakers?title=${Uri.encode(title)}")
                },
            )
        }
        composable(
            route = "recordings/{${RecordingSpeakersViewModel.ARG_RECORDING_ID}}/speakers" +
                "?title={${RecordingSpeakersViewModel.ARG_TITLE}}",
            arguments = listOf(
                navArgument(RecordingSpeakersViewModel.ARG_RECORDING_ID) { type = NavType.StringType },
                navArgument(RecordingSpeakersViewModel.ARG_TITLE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            RecordingSpeakersScreen(onBack = { nav.popBackStack() })
        }
    }
}
