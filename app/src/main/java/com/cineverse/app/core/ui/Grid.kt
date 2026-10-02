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
    return when {
        width >= 1000 -> 180.dp   // a tablet in landscape: 5-6 across
        width >= 700 -> 160.dp    // a tablet, or a phone unfolded
        width >= 600 -> 140.dp    // a large phone in landscape
        else -> 118.dp            // a phone: 3 across
    }
}

@Composable
@ReadOnlyComposable
fun posterGridCells(): GridCells = GridCells.Adaptive(posterCellWidth())
