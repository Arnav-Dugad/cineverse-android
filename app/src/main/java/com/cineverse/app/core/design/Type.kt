package com.cineverse.app.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * One typeface, as the website settled on: the platform's own interface face.
 * On Android that is Roboto (Roboto Flex on a recent device), which is what
 * [FontFamily.Default] resolves to — so the app inherits the user's chosen
 * system font and their font-size setting for free, and there is no 300 KB
 * of webfont in the APK to download or FOIT over.
 *
 * The scale below is the website's, converted: tracking is negative and grows
 * tighter as the type grows, and headline weights carry the hierarchy the
 * display serif used to.
 */
private val Face = FontFamily.Default

private val tightLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/**
 * Tabular figures: every digit the same width.
 *
 * Proportional digits are correct in prose and wrong everywhere a number
 * CHANGES. With the default figures a count-up from 0 to 1,284 reflows on almost
 * every frame, because a 1 is narrower than an 8 -- the number shivers, and a
 * row of them shivers out of step. `tnum` fixes the width and the shivering
 * stops. The same applies to the heatmap grid, a running clock, and any column
 * of counts that has to line up with the one above it.
 */
private const val TabularFigures = "tnum"

private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Double,
    align: TextAlign? = null,
) = TextStyle(
    fontFamily = Face,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    lineHeightStyle = tightLineHeight,
    textAlign = align ?: TextAlign.Unspecified,
)

val CineVerseTypography = Typography(
    // A title logo's fallback, and the one number on a stats hero.
    displayLarge = style(52, 56, FontWeight.W800, -1.6),
    displayMedium = style(42, 46, FontWeight.W800, -1.3),
    displaySmall = style(34, 40, FontWeight.W800, -1.0),

    headlineLarge = style(28, 34, FontWeight.W700, -0.7),
    headlineMedium = style(24, 30, FontWeight.W700, -0.6),
    headlineSmall = style(20, 26, FontWeight.W700, -0.45),

    // A rail's heading, a panel's heading, a card's title.
    titleLarge = style(19, 25, FontWeight.W700, -0.4),
    titleMedium = style(16, 22, FontWeight(650), -0.25),
    titleSmall = style(14, 19, FontWeight(650), -0.15),

    bodyLarge = style(16, 24, FontWeight.W400, 0.0),
    bodyMedium = style(14, 21, FontWeight.W400, 0.0),
    bodySmall = style(13, 19, FontWeight.W400, 0.0),

    // Meta under a poster, a chip, the caption on a stat.
    labelLarge = style(14, 18, FontWeight(650), 0.0),
    labelMedium = style(12, 16, FontWeight.W600, 0.1),
    labelSmall = style(11, 14, FontWeight.W600, 0.2),
)

/** The small capitals the website calls a kicker, in one muted ink. */
val KickerStyle = TextStyle(
    fontFamily = Face,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    fontWeight = FontWeight.W700,
    letterSpacing = 1.4.sp,
)

/** Figures that must line up: ratings, counts, durations, the heatmap. */
val NumberStyle = TextStyle(
    fontFamily = Face,
    fontWeight = FontWeight.W700,
    letterSpacing = (-0.2).sp,
    fontFeatureSettings = TabularFigures,
)

/** The same, as a modifier on any style a figure happens to be wearing. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = TabularFigures)
