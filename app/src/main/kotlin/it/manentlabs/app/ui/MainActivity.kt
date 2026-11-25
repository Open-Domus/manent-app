// SPDX-License-Identifier: MIT

package it.manentlabs.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import dagger.hilt.android.AndroidEntryPoint
import it.manentlabs.app.ui.theme.ManentTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Recording and the pendant link run as foreground services; without
        // this their notifications are hidden, though they still run.
        if (Build.VERSION.SDK_INT >= 33 && savedInstanceState == null) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { ManentTheme { ManentNavigation() } }
    }
}
