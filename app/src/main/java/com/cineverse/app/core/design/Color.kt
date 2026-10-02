package com.cineverse.app.core.design

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The palette, carried over from the website token for token (css/variables.css),
 * so the two read as one product rather than two apps that happen to share a name.
 *
 * Material's own scheme is derived from these at the bottom of the file; the
 * extended set below it is everything Material has no slot for — the gold a
 * rating wears, the seven heatmap bands, the hairline every surface is edged
 * with. Those live in [CvColors] and reach a composable through `CvTheme.colors`.
 */
object Palette {
    // Ink and paper. The five dark steps are the website's --bg … --bg5.
    val Ink = Color(0xFF06060B)
    val Ink2 = Color(0xFF0C0C14)
    val Ink3 = Color(0xFF14141F)
    val Ink4 = Color(0xFF1C1C2E)
    val Ink5 = Color(0xFF24243A)
    val Paper = Color(0xFFE6E2DA)
    val Paper2 = Color(0xFFEFEBE3)
    val Paper3 = Color(0xFFF6F3ED)

    val Red = Color(0xFFE50914)
    val Red2 = Color(0xFFFF2030)
    val Gold = Color(0xFFFBBF24)
    val Gold2 = Color(0xFFF59E0B)
    val Cyan = Color(0xFF06B6D4)
    val Cyan2 = Color(0xFF22D3EE)
    val Green = Color(0xFF10B981)
    val Green2 = Color(0xFF34D399)
    val Purple = Color(0xFF8B5CF6)
    val Purple2 = Color(0xFFA78BFA)
    val Pink = Color(0xFFEC4899)

    // Three steps of ink on dark. --text3 is the one the website had to raise to
    // #767f8d for contrast; it is the same here for the same reason.
    val TextDark = Color(0xFFF0F0F5)
    val TextDark2 = Color(0xFF9CA3AF)
    val TextDark3 = Color(0xFF767F8D)

    val TextLight = Color(0xFF1A1714)
    val TextLight2 = Color(0xFF55504A)
    val TextLight3 = Color(0xFF7A736A)

    /** The source marks, which are brand colours and never themed. */
    val ImdbYellow = Color(0xFFF5C518)
    val TomatoFresh = Color(0xFFFA320A)
    val TomatoLeaf = Color(0xFF3FBF63)
    val TomatoRotten = Color(0xFF7CB342)
    val PopcornFull = Color(0xFFFA9D0A)
    val MetaGood = Color(0xFF00CE7A)
    val MetaMixed = Color(0xFFFFBD3F)
    val MetaPoor = Color(0xFFFF6874)
}

/**
 * Everything Material 3 has no slot for. Read through `CvTheme.colors` so a
 * composable never has to know which theme is live.
 */
@Immutable
data class CvColors(
    val ink: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val gold: Color,
    val cyan: Color,
    val green: Color,
    val purple: Color,
    val pink: Color,
    /** The one hairline every surface is edged with. */
    val hairline: Color,
    /** The translucent fill a glass surface sits on, before its blur. */
    val glass: Color,
    val glassStrong: Color,
    /** The lip of light along a surface's top edge. */
    val lip: Color,
    val scrim: Color,
    /** The seven rating bands, under 6 through 9+, as the heatmap colours them. */
    val heat: List<Color>,
    val heatNone: Color,
    val isDark: Boolean,
)

val DarkCvColors = CvColors(
    ink = Palette.Ink,
    surface1 = Palette.Ink2,
    surface2 = Palette.Ink3,
    surface3 = Palette.Ink4,
    text = Palette.TextDark,
    text2 = Palette.TextDark2,
    text3 = Palette.TextDark3,
    gold = Palette.Gold,
    cyan = Palette.Cyan2,
    green = Palette.Green2,
    purple = Palette.Purple2,
    pink = Palette.Pink,
    hairline = Color(0x0FFFFFFF),
    glass = Color(0x08FFFFFF),
    glassStrong = Color(0x14FFFFFF),
    lip = Color(0x12FFFFFF),
    scrim = Color(0xCC06060B),
    heat = listOf(
        Color(0xFF7F1D1D), Color(0xFFB45309), Color(0xFFCA8A04),
        Color(0xFF9CA926), Color(0xFF65A30D), Color(0xFF16A34A), Color(0xFF34D399),
    ),
    heatNone = Color(0xFF3A3A4A),
    isDark = true,
)

val LightCvColors = CvColors(
    ink = Palette.Paper,
    surface1 = Palette.Paper2,
    surface2 = Palette.Paper3,
    surface3 = Color(0xFFFFFFFF),
    text = Palette.TextLight,
    text2 = Palette.TextLight2,
    text3 = Palette.TextLight3,
    gold = Color(0xFFB7791F),
    cyan = Color(0xFF0E7490),
    green = Color(0xFF047857),
    purple = Color(0xFF6D28D9),
    pink = Color(0xFFBE185D),
    hairline = Color(0x14000000),
    glass = Color(0x0A000000),
    glassStrong = Color(0x14000000),
    lip = Color(0x40FFFFFF),
    scrim = Color(0xCCE6E2DA),
    heat = listOf(
        Color(0xFFB91C1C), Color(0xFFD97706), Color(0xFFCA8A04),
        Color(0xFF84A017), Color(0xFF4D7C0F), Color(0xFF15803D), Color(0xFF047857),
    ),
    heatNone = Color(0xFFCFC9BE),
    isDark = false,
)

internal val CineVerseDarkScheme = darkColorScheme(
    primary = Palette.Red2,
    onPrimary = Color.White,
    primaryContainer = Palette.Red,
    onPrimaryContainer = Color.White,
    secondary = Palette.Gold,
    onSecondary = Color(0xFF231A00),
    tertiary = Palette.Cyan2,
    onTertiary = Color(0xFF00242D),
    background = Palette.Ink,
    onBackground = Palette.TextDark,
    surface = Palette.Ink,
    onSurface = Palette.TextDark,
    surfaceVariant = Palette.Ink3,
    onSurfaceVariant = Palette.TextDark2,
    surfaceContainerLowest = Palette.Ink,
    surfaceContainerLow = Palette.Ink2,
    surfaceContainer = Palette.Ink3,
    surfaceContainerHigh = Palette.Ink4,
    surfaceContainerHighest = Palette.Ink5,
    outline = Color(0xFF3A3A48),
    outlineVariant = Color(0xFF26262F),
    error = Palette.Red2,
    scrim = Color.Black,
)

internal val CineVerseLightScheme = lightColorScheme(
    primary = Palette.Red,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF8A6100),
    onSecondary = Color.White,
    tertiary = Color(0xFF0E7490),
    onTertiary = Color.White,
    background = Palette.Paper,
    onBackground = Palette.TextLight,
    surface = Palette.Paper,
    onSurface = Palette.TextLight,
    surfaceVariant = Palette.Paper3,
    onSurfaceVariant = Palette.TextLight2,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Palette.Paper3,
    surfaceContainer = Palette.Paper2,
    surfaceContainerHigh = Color(0xFFE2DED6),
    surfaceContainerHighest = Color(0xFFDAD6CE),
    outline = Color(0xFFA9A299),
    outlineVariant = Color(0xFFCFC9BE),
    error = Color(0xFFB3261E),
    scrim = Color.Black,
)
