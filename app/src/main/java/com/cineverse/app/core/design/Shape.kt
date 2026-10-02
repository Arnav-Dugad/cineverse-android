package com.cineverse.app.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * One radius scale, four steps, each about 1.4x the last — the same scale the
 * website settled on, for the same reason: a control inside a panel should
 * always be visibly rounder or flatter than the panel around it, and that only
 * holds if the steps are far enough apart to see.
 *
 * Anything that is a pill stays a pill ([CvShape.Pill]); nothing in the app
 * invents a one-off radius.
 */
object CvShape {
    val Tiny = RoundedCornerShape(6.dp)
    /** Chips, small marks, a rating badge. */
    val Small = RoundedCornerShape(10.dp)
    /** Buttons, fields, a season chip, an episode still. */
    val Medium = RoundedCornerShape(14.dp)
    /** Posters, cards, rows. */
    val Large = RoundedCornerShape(20.dp)
    /** Panels, the segmented control's track, a stats block. */
    val XLarge = RoundedCornerShape(26.dp)
    /** Sheets and dialogs — the biggest surfaces in the app. */
    val XXLarge = RoundedCornerShape(34.dp)

    /** A bottom sheet is only rounded where it leaves the edge. */
    val Sheet = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)

    val Pill = RoundedCornerShape(percent = 50)
    val Circle = RoundedCornerShape(percent = 50)
}

val CineVerseShapes = Shapes(
    extraSmall = CvShape.Tiny,
    small = CvShape.Small,
    medium = CvShape.Medium,
    large = CvShape.Large,
    extraLarge = CvShape.XLarge,
)
