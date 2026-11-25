// SPDX-License-Identifier: MIT

package it.manentlabs.app.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The side margin of every page. */
val PageGutter = 20.dp

/** The space between two numbered sections, generous as on the website. */
val SectionGap = 44.dp

/**
 * A screen: the top bar (wordmark when [title] is null), then a scrolling
 * column of sections with the page's margins and rhythm.
 */
@Composable
fun ManentPage(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(containerColor = Manent.colors.paper, topBar = { ManentTopBar(title, onBack) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = PageGutter, end = PageGutter, top = 28.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(SectionGap),
            content = content,
        )
    }
}
