package com.cineverse.app.feature.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.ui.CvDropdown
import com.cineverse.app.core.ui.CvTogglePill
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.data.model.Genre
import com.cineverse.app.data.model.MediaFilter
import com.cineverse.app.data.model.SortOrder
import com.cineverse.app.data.model.TypeFilter

/**
 * Narrowing a list down, the website's way: a row of select boxes, each one
 * showing its choice once made, applied the moment it is picked.
 *
 * This replaced a bottom sheet of chips. A sheet hides what is filtering the
 * grid until it is opened again; a row of filled pills says it all the time,
 * and changing one is one tap and a pick rather than open, scroll, tap, close.
 *
 * [count] is the line above the row - "42 of 144" - so a filter that has
 * emptied the grid is never a mystery.
 */
@Composable
fun FilterDropdowns(
    filter: MediaFilter,
    genres: List<Genre>,
    onChange: (MediaFilter) -> Unit,
    modifier: Modifier = Modifier,
    count: String? = null,
    showType: Boolean = true,
    showHideWatched: Boolean = false,
    /** The sort pill, which differs by screen: search ranks, a list orders. */
    sort: @Composable () -> Unit = {},
    /** Whether the sort is off its default, so Reset knows to show. */
    sortActive: Boolean = false,
    onReset: () -> Unit = { onChange(filter.clear()) },
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val any = !filter.isDefault || sortActive
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).height(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                count.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.text3,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            AnimatedVisibility(any, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    "Reset",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.text2,
                    modifier = Modifier
                        .clip(CvShape.Pill)
                        .clickableNoRipple { haptics?.play(Haptic.Untick); onReset() }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val summary = buildList {
            if (showType && filter.type != TypeFilter.All) add(filter.type.label)
            genres.firstOrNull { it.id == filter.genreId }?.let { add(it.name) }
            if (filter.minRating > 0) add("${filter.minRating}+")
            if (filter.decade > 0) add("${filter.decade}s")
            if (showHideWatched && filter.hideWatched) add("Unwatched")
        }
        com.cineverse.app.core.ui.FoldedFilters(
            summary = summary,
            horizontalPadding = ScreenPadding,
            always = sort,
        ) {
            if (showType) {
                CvDropdown("Type", TypeFilter.entries.map { it to if (it == TypeFilter.All) "Films and series" else it.label }, filter.type) {
                    onChange(filter.copy(type = it))
                }
            }
            if (genres.isNotEmpty()) {
                CvDropdown("Genre", listOf(0 to "All genres") + genres.map { it.id to it.name }, filter.genreId) {
                    onChange(filter.copy(genreId = it))
                }
            }
            CvDropdown("Rating", listOf(0 to "Any rating", 6 to "6+", 7 to "7+", 8 to "8+", 9 to "9+"), filter.minRating) {
                onChange(filter.copy(minRating = it))
            }
            CvDropdown("Decade", listOf(0 to "Any decade") + MediaFilter.decades().map { it to "${it}s" }, filter.decade) {
                onChange(filter.copy(decade = it))
            }
            if (showHideWatched) {
                CvTogglePill("Hide watched", filter.hideWatched) { onChange(filter.copy(hideWatched = it)) }
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}

/** The sort pill for a list of [SortOrder]s: search's. */
@Composable
fun SortDropdown(sorts: List<SortOrder>, filter: MediaFilter, onChange: (MediaFilter) -> Unit) {
    CvDropdown("Sort", sorts.map { it to it.label }, filter.sort) { onChange(filter.copy(sort = it)) }
}
