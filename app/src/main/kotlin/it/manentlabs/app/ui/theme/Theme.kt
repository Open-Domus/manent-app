// SPDX-License-Identifier: MIT

package it.manentlabs.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Manent's palette, the one the website uses: warm paper, near-black ink and a
 * terracotta accent, each with a dark counterpart. Status colors sit beside it
 * for what the app reports (linked, failed, needs attention).
 */
@Immutable
data class ManentColors(
    val paper: Color,
    val surface: Color,
    val surfaceDeep: Color,
    val ink: Color,
    val muted: Color,
    val rule: Color,
    val accent: Color,
    val onAccent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
)

val LightColors = ManentColors(
    paper = Color(0xFFF4EFE6),
    surface = Color(0xFFEAE3D6),
    surfaceDeep = Color(0xFFD9D0BF),
    ink = Color(0xFF16130F),
    muted = Color(0xFF6B6257),
    rule = Color(0x3316130F),
    accent = Color(0xFFC42B17),
    onAccent = Color(0xFFFFF8F0),
    success = Color(0xFF2F6B35),
    warning = Color(0xFF8A5A00),
    danger = Color(0xFFA4161A),
)

val DarkColors = ManentColors(
    paper = Color(0xFF14110E),
    surface = Color(0xFF1E1A16),
    surfaceDeep = Color(0xFF2C2620),
    ink = Color(0xFFEFE8DC),
    muted = Color(0xFFA69C8D),
    rule = Color(0x38EFE8DC),
    accent = Color(0xFFF2634B),
    onAccent = Color(0xFF14110E),
    success = Color(0xFF7DC283),
    warning = Color(0xFFE0B25A),
    danger = Color(0xFFFF8A80),
)

private val LocalManentColors = staticCompositionLocalOf { LightColors }

/** The palette of the current theme, for what Material's roles do not name. */
object Manent {
    val colors: ManentColors
        @Composable @ReadOnlyComposable get() = LocalManentColors.current
}

/** Square corners, as on the website; only the smallest controls soften a little. */
private val ManentShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

@Composable
fun ManentTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            primaryContainer = c.accent.copy(alpha = 0.18f), onPrimaryContainer = c.ink,
            secondary = c.muted, onSecondary = c.paper,
            secondaryContainer = c.surface, onSecondaryContainer = c.ink,
            background = c.paper, onBackground = c.ink,
            surface = c.paper, onSurface = c.ink,
            surfaceVariant = c.surface, onSurfaceVariant = c.muted,
            surfaceContainerLowest = c.paper, surfaceContainerLow = c.surface,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surfaceDeep,
            outline = c.rule, outlineVariant = c.rule,
            error = c.danger, onError = c.paper,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            primaryContainer = c.accent.copy(alpha = 0.12f), onPrimaryContainer = c.ink,
            secondary = c.muted, onSecondary = c.paper,
            secondaryContainer = c.surface, onSecondaryContainer = c.ink,
            background = c.paper, onBackground = c.ink,
            surface = c.paper, onSurface = c.ink,
            surfaceVariant = c.surface, onSurfaceVariant = c.muted,
            surfaceContainerLowest = c.paper, surfaceContainerLow = c.surface,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surfaceDeep,
            outline = c.rule, outlineVariant = c.rule,
            error = c.danger, onError = c.paper,
        )
    }
    CompositionLocalProvider(LocalManentColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = ManentTypography, shapes = ManentShapes, content = content)
    }
}
