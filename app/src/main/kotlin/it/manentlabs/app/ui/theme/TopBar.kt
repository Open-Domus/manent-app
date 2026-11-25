// SPDX-License-Identifier: MIT

package it.manentlabs.app.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import it.manentlabs.app.R

/**
 * The bar over every screen: the MANENT wordmark on Home, a serif title with a
 * way back elsewhere. A hairline separates it from the page, as on the website.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManentTopBar(title: String? = null, onBack: (() -> Unit)? = null) {
    Column {
        CenterAlignedTopAppBar(
            title = {
                if (title == null) {
                    Text(stringResource(R.string.app_name).uppercase(), style = ManentText.wordmark)
                } else {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                }
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.app_back))
                    }
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Manent.colors.paper,
                scrolledContainerColor = Manent.colors.paper,
                titleContentColor = Manent.colors.ink,
                navigationIconContentColor = Manent.colors.ink,
            ),
        )
        Hairline()
    }
}
