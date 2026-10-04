package com.cineverse.app.core.ui

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How many posters across, and so how wide each one is.
 *
 * This used to be `GridCells.Adaptive` with a minimum cell width per setting,
 * and on a phone that made the setting do nothing: 118dp and 150dp minimums
 * both come out at two columns on a 411dp screen. A COLUMN COUNT is what the
 * setting actually means, so that is what it sets - four, three or two on a
 * phone - and wider windows add columns rather than blowing posters up.
 */
@Composable
@ReadOnlyComposable
fun posterColumns(): Int {
    val width = LocalConfiguration.current.screenWidthDp
    val base = LocalGridDensity.current.columns
    return when {
        width >= 1000 -> base + 3
        width >= 700 -> base + 2
        width >= 600 -> base + 1
        else -> base
    }
}

/** The exact width of one cell, so a card fills its column with no gap left over. */
@Composable
@ReadOnlyComposable
fun posterCellWidth(): Dp {
    val width = LocalConfiguration.current.screenWidthDp.dp
    val columns = posterColumns()
    return (width - ScreenPadding * 2 - GridGap * (columns - 1)) / columns
}

/** The gap between grid cells, matching every grid's spacedBy(12.dp). */
val GridGap = 12.dp

/** A rail card's width, following the same setting. */
@Composable
@ReadOnlyComposable
fun railCardWidth(): Dp = LocalGridDensity.current.railDp.dp

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
fun posterGridCells(): GridCells = GridCells.Fixed(posterColumns())
