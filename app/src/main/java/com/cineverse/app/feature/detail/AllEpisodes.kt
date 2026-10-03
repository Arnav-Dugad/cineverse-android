package com.cineverse.app.feature.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Motion
import com.cineverse.app.core.ui.glass
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.data.model.Episode
import com.cineverse.app.data.model.ShowProgress

/**
 * Every episode of a show, in one box you scroll inside.
 *
 * The season picker answers "what is in season four". This answers "where am I
 * in the whole thing", which on a show with twenty-three seasons is a different
 * question and one the app could not answer at all: you had to pick a season,
 * read it, pick the next, and hold the running total in your head.
 *
 * It is a BOUNDED scroller on purpose. Letting a thousand episodes flow into
 * the page would make the title page a mile long and bury everything under it;
 * a box with its own scroll keeps the rest of the page reachable and makes the
 * list feel like a thing you are looking into rather than a thing you are lost
 * in. The height is fixed rather than fractional so the bottom edge is always
 * visible, which is what tells you it scrolls.
 *
 * Collapsed by default, and the fetch only happens on first open: a
 * twenty-three season show is twenty-three requests, and nobody who never opens
 * this should pay for them.
 */
@Composable
fun AllEpisodesPanel(
    seasons: Map<Int, List<Episode>>,
    /** How many seasons the show HAS, so a partial map can be recognised. */
    seasonCount: Int,
    progress: ShowProgress?,
    expanded: Boolean,
    loading: Boolean,
    spoilerShield: Boolean,
    onToggle: () -> Unit,
    onToggleEpisode: (Int, Int) -> Unit,
    onMarkUpTo: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val turn by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = Motion.lively(),
        label = "allEpisodes",
    )

    // Flattened once, with a header row before each season, so the inner list
    // can stay a plain LazyColumn with stable keys.
    val rows = remember(seasons, progress) {
        buildList {
            for (season in seasons.keys.sorted()) {
                val episodes = seasons[season].orEmpty()
                if (episodes.isEmpty()) continue
                add(Row.Header(season, episodes.count { it.hasAired }, episodes.size))
                for (episode in episodes) add(Row.Item(episode))
            }
        }
    }
    val watched = remember(rows, progress) {
        rows.count { it is Row.Item && progress?.isWatched(it.episode.season, it.episode.number) == true }
    }
    val aired = remember(rows) { rows.count { it is Row.Item && it.episode.hasAired } }
    val loadedAll = seasons.size >= seasonCount && seasonCount > 0

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding)
            .glass(CvShape.XLarge)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickableNoRipple { haptics?.play(Haptic.Tap); onToggle() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("THE WHOLE RUN", style = KickerStyle, color = colors.text3)
                Spacer(Modifier.height(4.dp))
                Text(
                    "All episodes",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                )
                // Only once EVERY season is loaded. Before the panel is opened
                // the map holds whichever season the page happens to be showing,
                // and counting that said "10 of 10 aired episodes seen" for a
                // show with four seasons and ninety episodes.
                Text(
                    if (loadedAll) "$watched of $aired aired episodes seen"
                    else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.text3,
                )
            }
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = colors.text3,
                modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = turn },
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(Motion.size()) + fadeIn(),
            exit = shrinkVertically(Motion.size()) + fadeOut(),
        ) {
            when {
                loading && rows.isEmpty() -> Box(
                    Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.text3, strokeWidth = 2.dp)
                }

                rows.isEmpty() -> Text(
                    "No episodes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text3,
                    modifier = Modifier.padding(16.dp),
                )

                else -> Box {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().height(480.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 8.dp, end = 8.dp, bottom = 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(rows, key = { it.key }) { row ->
                            when (row) {
                                is Row.Header -> SeasonHeader(row)
                                is Row.Item -> EpisodeRow(
                                    episode = row.episode,
                                    watched = progress?.isWatched(
                                        row.episode.season,
                                        row.episode.number,
                                    ) == true,
                                    isNext = false,
                                    watchedAt = progress?.watchedAt(
                                        row.episode.season,
                                        row.episode.number,
                                    ) ?: 0L,
                                    spoilerShield = spoilerShield,
                                    onToggle = {
                                        onToggleEpisode(row.episode.season, row.episode.number)
                                    },
                                    onMarkUpTo = {
                                        onMarkUpTo(row.episode.season, row.episode.number)
                                    },
                                    onOpen = {
                                        onToggleEpisode(row.episode.season, row.episode.number)
                                    },
                                )
                            }
                        }
                    }
                    // A fade at the bottom edge, which is what says "there is
                    // more below" on a box that has no scrollbar of its own.
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, colors.ink.copy(alpha = 0.75f))
                                )
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun SeasonHeader(header: Row.Header) {
    val colors = CvTheme.colors
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp, start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (header.season == 0) "Specials" else "Season ${header.season}",
            style = MaterialTheme.typography.titleSmall,
            color = colors.text,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (header.aired < header.total) "${header.aired} of ${header.total} aired"
            else "${header.total} episodes",
            style = MaterialTheme.typography.labelSmall,
            color = colors.text3,
        )
    }
}

private sealed interface Row {
    val key: String

    data class Header(val season: Int, val aired: Int, val total: Int) : Row {
        override val key: String get() = "h$season"
    }

    data class Item(val episode: Episode) : Row {
        override val key: String get() = "e${episode.season}_${episode.number}"
    }
}
