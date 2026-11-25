// SPDX-License-Identifier: MIT

package it.manentlabs.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import it.manentlabs.app.R

/**
 * The website's three voices: Bodoni Moda for titles, Titillium Web for
 * reading, IBM Plex Mono for the small uppercase labels and figures. All three
 * are under the SIL Open Font License (see assets/licences).
 */
object ManentFonts {
    val serif = FontFamily(
        Font(R.font.bodoni_medium, FontWeight.Medium),
        Font(R.font.bodoni_semibold, FontWeight.SemiBold),
    )
    val sans = FontFamily(
        Font(R.font.titillium_light, FontWeight.Light),
        Font(R.font.titillium_regular, FontWeight.Normal),
        Font(R.font.titillium_semibold, FontWeight.SemiBold),
    )
    val mono = FontFamily(
        Font(R.font.plex_mono_regular, FontWeight.Normal),
        Font(R.font.plex_mono_medium, FontWeight.Medium),
    )
}

/** Text sits on its line without the extra padding Android adds by default. */
private val tight = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

private fun serif(size: Int, line: Int) = tight.copy(
    fontFamily = ManentFonts.serif, fontWeight = FontWeight.Medium,
    fontSize = size.sp, lineHeight = line.sp, letterSpacing = (-0.02).em,
)

private fun sans(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = tight.copy(
    fontFamily = ManentFonts.sans, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
)

private fun mono(size: Int, line: Int) = tight.copy(
    fontFamily = ManentFonts.mono, fontWeight = FontWeight.Medium,
    fontSize = size.sp, lineHeight = line.sp, letterSpacing = 0.06.em,
)

/**
 * display: large figures (the recording clock uses [ManentText.figure] instead).
 * headline: a section's serif title. titleLarge: a screen's serif title.
 * titleMedium/Small: a sub-heading in the reading face.
 * label{Medium,Small}: mono, meant for uppercase eyebrows and status lines.
 */
val ManentTypography = Typography(
    displayLarge = serif(52, 54),
    displayMedium = serif(44, 46),
    displaySmall = serif(36, 38),
    headlineLarge = serif(34, 36),
    headlineMedium = serif(30, 32),
    headlineSmall = serif(26, 29),
    titleLarge = serif(24, 28),
    titleMedium = sans(17, 22, FontWeight.SemiBold),
    titleSmall = sans(15, 20, FontWeight.SemiBold),
    bodyLarge = sans(17, 25),
    bodyMedium = sans(15, 22),
    bodySmall = sans(13, 19),
    labelLarge = sans(15, 18, FontWeight.SemiBold).copy(letterSpacing = 0.02.em),
    labelMedium = mono(12, 16),
    labelSmall = mono(11, 14),
)

/** Styles Material's scale has no name for. */
object ManentText {
    /** The intro under a section title, light like the website's lede. */
    val lede = sans(17, 25, FontWeight.Light)

    /** Figures that tick, such as the recording clock: monospaced so they do not jump. */
    val figure = tight.copy(fontFamily = ManentFonts.mono, fontWeight = FontWeight.Normal, fontSize = 44.sp, lineHeight = 48.sp)

    /** The wordmark in the top bar. */
    val wordmark = tight.copy(
        fontFamily = ManentFonts.serif, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = 0.3.em,
    )
}
