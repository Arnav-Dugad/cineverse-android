package com.cineverse.app.core.ui

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How wide a poster should be, for the screen it is on.
 *
 * `GridCells.Adaptive` with one fixed minimum is right on a phone and wrong
 * everywhere else: on a tablet it packs eight tiny posters across a 1200dp
 * window, and in landscape it does the same to a phone. The minimum grows with
 * the window so the COLUMN COUNT stays in a sensible range rather than the card
 * size staying constant.
 */
@Composable
@ReadOnlyComposable
fun posterCellWidth(): Dp {
    val width = LocalConfiguration.current.screenWidthDp
    // The user's own preference is the base, and the window scales it. Letting
    // the preference set an absolute size would make "More per row" mean eight
    // columns on a tablet and three on a phone; letting the window decide alone
    // ignores the setting entirely.
    val base = LocalGridDensity.current.cellDp
    val scale = when {
        width >= 1000 -> 1.5f
        width >= 700 -> 1.35f
        width >= 600 -> 1.18f
        else -> 1f
    }
    return (base * scale).dp
}

/**
 * How tightly posters are packed, from Settings.
 *
 * A composition local rather than a parameter: every grid in the app wants it,
 * and threading it through nine screens to reach a cell width would be nine
 * signatures changed for one number.
 */
val LocalGridDensity = androidx.compose.runtime.staticCompositionLocalOf {
    com.cineverse.app.data.prefs.GridDensity.Comfortable
}

@Composable
@ReadOnlyComposable
fun posterGridCells(): GridCells = GridCells.Adaptive(posterCellWidth())
