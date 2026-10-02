package com.cineverse.app.feature.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Motion
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.Img
import com.cineverse.app.core.ui.ScoreRow
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.sharedPoster
import com.cineverse.app.core.ui.SectionHeader
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.core.ui.shimmer
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.Person
import com.cineverse.app.data.model.ShowProgress
import com.cineverse.app.data.model.TitleDetail
import com.cineverse.app.data.scores.Scores

/**
 * Poster, title, every score, and one row of actions.
 *
 * One filled button and four icon buttons: on a phone there is room for exactly
 * one primary action, and on a tracker that action is the trailer — everything
 * else is a toggle you will press a thousand times and should therefore be a
 * target, not a label.
 */
@Composable
fun DetailHead(
    detail: TitleDetail,
    scores: Scores,
    saved: Boolean,
    watched: Boolean,
    myRating: Int,
    movieMinutes: Int,
    onSave: () -> Unit,
    onWatched: () -> Unit,
    onRate: () -> Unit,
    onShare: () -> Unit,
    onPlayTrailer: () -> Unit,
    onLists: () -> Unit,
    onProgress: () -> Unit,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current

    Column(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding)) {
        // Poster left, everything that describes the title right.
        //
        // The first cut stacked the scores and the genres BELOW this row, which
        // on a title with a hero logo left a hand-sized hole beside the poster:
        // the right column held a tagline and a date line and then 90dp of
        // nothing, while the information that would have filled it sat
        // underneath taking another two rows of height. Moving it into the
        // column closes the hole and shortens the block at the same time.
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .width(112.dp)
                    .height(168.dp)
                    .sharedPoster(detail.key)
                    .clip(CvShape.Large)
                    .border(1.dp, colors.hairline, CvShape.Large)
            ) {
                CvImage(Img.posterLarge(detail.posterPath), detail.title, Modifier.fillMaxSize())
            }
            Column(
                Modifier
                    .padding(start = 14.dp)
                    .height(168.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // The name, only where the hero did not already carry it as a
                // logo. Printing both is the commonest way a title page ends up
                // saying the same thing twice in two typefaces.
                if (detail.logoPath == null) {
                    Text(
                        detail.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.text,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val meta = listOfNotNull(
                    detail.year.takeIf { it.isNotBlank() },
                    detail.certificate.takeIf { it.isNotBlank() },
                    runtimeLabel(detail),
                ).joinToString("  ·  ")
                Text(meta, style = MaterialTheme.typography.labelMedium, color = colors.text2)

                if (detail.tagline.isNotBlank()) {
                    Text(
                        "“${detail.tagline}”",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.text3,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Pushes the scores and the genres to the bottom of the column,
                // so they sit on the poster baseline however tall the text above
                // them turned out to be.
                Spacer(Modifier.weight(1f))

                // CineVerse own score first, then the outside ones as they land.
                // It scrolls because three outside scores plus ours is wider
                // than a phone, and truncating a score is worse than scrolling.
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (detail.voteAverage > 0) {
                        Row(
                            Modifier
                                .height(28.dp)
                                .clip(CvShape.Pill)
                                .background(colors.gold.copy(alpha = 0.16f))
                                .padding(horizontal = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Star,
                                null,
                                tint = colors.gold,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                String.format("%.1f", detail.voteAverage),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.gold,
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    ScoreRow(scores)
                }

                if (detail.genres.isNotEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (genre in detail.genres.take(3)) {
                            Box(
                                Modifier
                                    .clip(CvShape.Pill)
                                    .background(colors.glass)
                                    .border(1.dp, colors.hairline, CvShape.Pill)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    genre.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.text2,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (detail.trailer != null) {
                Button(
                    onClick = { haptics?.play(Haptic.Tap); onPlayTrailer() },
                    shape = CvShape.Pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Palette.Red,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.height(46.dp).weight(1f),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Trailer", style = MaterialTheme.typography.labelLarge)
                }
            }
            // Tap saves, hold chooses where. The hold is the only way into the
            // custom lists from here, so it gets the Peek signature that the
            // rest of the app uses for "there is more behind this".
            ActionButton(
                icon = if (saved) Icons.Rounded.Bookmark else Icons.Rounded.Add,
                active = saved,
                description = if (saved) "In your list" else "Add to your list",
                onLongPress = { haptics?.play(Haptic.Peek); onLists() },
            ) { haptics?.play(if (saved) Haptic.Untick else Haptic.Tick); onSave() }

            ActionButton(
                icon = Icons.Rounded.Check,
                active = watched,
                activeTint = colors.green,
                description = if (watched) "Watched" else "Mark watched",
            ) { haptics?.play(if (watched) Haptic.Untick else Haptic.Tick); onWatched() }

            ActionButton(
                icon = if (myRating > 0) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                active = myRating > 0,
                activeTint = colors.gold,
                label = if (myRating > 0) myRating.toString() else null,
                description = "Rate",
            ) { haptics?.play(Haptic.Tap); onRate() }

            ActionButton(icon = Icons.Rounded.Share, active = false, description = "Share") {
                haptics?.play(Haptic.Tap); onShare()
            }
        }

        // A film you are part way through. Only for films, only when there is a
        // runtime to be part way through, and only once there is progress or
        // the title is unwatched — a finished film does not need a scrubber.
        if (!detail.isSeries && detail.runtime > 0 && (movieMinutes > 0 || !watched)) {
            Spacer(Modifier.height(16.dp))
            MovieProgressStrip(
                minutes = movieMinutes,
                runtime = detail.runtime,
                onClick = { haptics?.play(Haptic.Tap); onProgress() },
            )
        }

        if (detail.overview.isNotBlank()) {
            Spacer(Modifier.height(18.dp))
            ExpandableText(detail.overview)
        }
    }
}

/**
 * The one-line read on a film you paused.
 *
 * It states the time rather than only drawing a bar, because "48m left" is the
 * thing being decided and a bar at 62% is not.
 */
@Composable
private fun MovieProgressStrip(minutes: Int, runtime: Int, onClick: () -> Unit) {
    val colors = CvTheme.colors
    val started = minutes > 0
    val fraction = (minutes.toFloat() / runtime).coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = Motion.landing(),
        label = "filmProgress",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CvShape.Medium)
            .background(colors.glass)
            .border(1.dp, colors.hairline, CvShape.Medium)
            .clickableNoRipple(onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (started) "${runtime - minutes}m left" else "Set your place",
                style = MaterialTheme.typography.labelLarge,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (started) "${(fraction * 100).toInt()}%" else "Not started",
                style = MaterialTheme.typography.labelMedium,
                color = colors.text3,
            )
        }
        if (started) {
            Spacer(Modifier.height(9.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CvShape.Pill)
                    .background(colors.text.copy(alpha = 0.14f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(animated)
                        .height(4.dp)
                        .clip(CvShape.Pill)
                        .background(Palette.Red2)
                )
            }
        }
    }
}

private fun runtimeLabel(detail: TitleDetail): String? = when {
    detail.isSeries && detail.numberOfSeasons > 0 ->
        "${detail.numberOfSeasons} season${if (detail.numberOfSeasons == 1) "" else "s"}"
    detail.runtime > 0 -> "${detail.runtime / 60}h ${detail.runtime % 60}m"
    else -> null
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    description: String,
    activeTint: Color = Color.Unspecified,
    label: String? = null,
    onLongPress: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = CvTheme.colors
    val tint = when {
        !active -> colors.text
        activeTint != Color.Unspecified -> activeTint
        else -> Palette.Red2
    }
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 0.98f,
        animationSpec = Motion.lively(),
        label = "action",
    )
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(if (active) tint.copy(alpha = 0.14f) else colors.glass)
            .border(1.dp, if (active) tint.copy(alpha = 0.5f) else colors.hairline, CircleShape)
            .then(
                if (onLongPress == null) Modifier.clickableNoRipple(onClick)
                else Modifier.pointerInput(onClick, onLongPress) {
                    detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() })
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = tint)
        } else {
            Icon(
                icon,
                contentDescription = description,
                tint = tint,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

/** Three lines, then "Read more" — and only when the text actually overflows. */
@Composable
fun ExpandableText(text: String, collapsedLines: Int = 3) {
    val colors = CvTheme.colors
    var expanded by remember { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    Column(Modifier.animateContentSize(Motion.size())) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text2,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result -> if (!expanded) overflows = result.hasVisualOverflow },
        )
        if (overflows || expanded) {
            Text(
                if (expanded) "Read less" else "Read more",
                style = MaterialTheme.typography.labelMedium,
                color = colors.text,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickableNoRipple { expanded = !expanded },
            )
        }
    }
}

/**
 * Episodes / About / More like this.
 *
 * A segmented control rather than a tab row: three fixed options that fit on one
 * line. Tabs that scroll sideways hide their own options, which on a three-way
 * choice is a thing to avoid.
 *
 * The selection SLIDES. A highlight that teleports between segments tells you
 * which one is active; one that travels tells you which one you came from, and
 * on a control you hit dozens of times an evening that is the difference between
 * a widget and a place. The pill is one layer that moves, not three that
 * recolour — the same reason it can be animated on the compositor for free.
 */
@Composable
fun SegmentedTabs(
    tabs: List<DetailTab>,
    selected: DetailTab,
    onSelect: (DetailTab) -> Unit,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val index = tabs.indexOf(selected).coerceAtLeast(0)
    val slide by animateFloatAsState(
        targetValue = index.toFloat(),
        animationSpec = Motion.landing(),
        label = "segment",
    )
    // Swipe the control itself to move between segments. Put on the CONTROL and
    // not on the content below it: a horizontal drag anywhere in a vertically
    // scrolling page of rails would be a gesture fighting three other gestures,
    // and the one place a sideways swipe is unambiguous is the thing that
    // already looks like a row of options.
    var travel by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.ink)
            .padding(horizontal = ScreenPadding, vertical = 12.dp)
            .pointerInput(tabs, selected) {
                detectHorizontalDragGestures(
                    onDragStart = { travel = 0f },
                    onDragEnd = { travel = 0f },
                    onDragCancel = { travel = 0f },
                ) { _, delta ->
                    travel += delta
                    // 56px of committed travel, then one step. Accumulating and
                    // resetting means a long drag steps once per threshold
                    // rather than racing through every tab at once.
                    val step = when {
                        travel <= -56f -> 1
                        travel >= 56f -> -1
                        else -> 0
                    }
                    if (step != 0) {
                        travel = 0f
                        val next = tabs.getOrNull(index + step)
                        if (next != null) {
                            haptics?.play(Haptic.Select)
                            onSelect(next)
                        } else {
                            haptics?.play(Haptic.Edge)
                        }
                    }
                }
            }
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(CvShape.Pill)
                .background(colors.glass)
                .border(1.dp, colors.hairline, CvShape.Pill)
                .padding(3.dp),
        ) {
            val slot = maxWidth / tabs.size.coerceAtLeast(1)
            Box(
                Modifier
                    .offset(x = slot * slide)
                    .width(slot)
                    .fillMaxHeight()
                    .clip(CvShape.Pill)
                    .background(colors.text.copy(alpha = 0.14f))
            )
            Row(Modifier.fillMaxSize()) {
                for (tab in tabs) {
                    val active = tab == selected
                    // The label weight does not animate with the pill: text that
                    // changes weight mid-slide reflows, and a reflowing label
                    // under a moving highlight looks like a rendering fault.
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CvShape.Pill)
                            .clickableNoRipple {
                                if (!active) haptics?.play(Haptic.Select)
                                onSelect(tab)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) colors.text else colors.text3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ---------- Episodes ----------

fun LazyListScope.episodesSection(
    state: DetailState,
    progress: ShowProgress?,
    spoilerShield: Boolean,
    onSeason: (Int) -> Unit,
    onToggle: (Int, Int) -> Unit,
    onMarkUpTo: (Int, Int) -> Unit,
    onSeasonWatched: (Int, Boolean) -> Unit,
    onHeatmapToggle: () -> Unit,
    onHeatMode: (com.cineverse.app.data.model.HeatMode) -> Unit,
    onNumbers: () -> Unit,
    onOpenEpisode: (Int, Int) -> Unit,
) {
    val detail = state.detail ?: return
    val next = progress?.nextUp()

    item(key = "heatmap") {
        HeatmapPanel(
            heatmap = state.heatmap,
            loading = state.loadingHeatmap,
            expanded = state.heatmapOpen,
            mode = state.heatMode,
            numbers = state.showNumbers,
            onToggle = onHeatmapToggle,
            onMode = onHeatMode,
            onNumbers = onNumbers,
            onOpenEpisode = onOpenEpisode,
            modifier = Modifier.padding(bottom = 14.dp),
        )
    }

    item(key = "seasons") {
        SeasonChips(
            detail = detail,
            progress = progress,
            selected = state.season,
            onSelect = onSeason,
            onMarkSeason = onSeasonWatched,
        )
    }

    if (state.loadingEpisodes) {
        items(5, key = { "skel_$it" }) {
            Box(
                Modifier
                    .padding(horizontal = ScreenPadding, vertical = 8.dp)
                    .fillMaxWidth()
                    .height(70.dp)
                    .clip(CvShape.Large)
                    .shimmer()
            )
        }
        return
    }

    items(state.episodes, key = { "ep_${it.season}_${it.number}" }) { episode ->
        EpisodeRow(
            episode = episode,
            watched = progress?.isWatched(episode.season, episode.number) == true,
            isNext = next?.first == episode.season && next.second == episode.number,
            watchedAt = progress?.watchedAt(episode.season, episode.number) ?: 0L,
            spoilerShield = spoilerShield,
            onToggle = { onToggle(episode.season, episode.number) },
            onMarkUpTo = { onMarkUpTo(episode.season, episode.number) },
            onOpen = { onToggle(episode.season, episode.number) },
            modifier = Modifier.padding(horizontal = ScreenPadding - 4.dp),
        )
    }
}

/**
 * The season picker: a chip rail, not a dropdown — one tap instead of two, and
 * every season can show how far through it you are without being opened.
 */
@Composable
private fun SeasonChips(
    detail: TitleDetail,
    progress: ShowProgress?,
    selected: Int,
    onSelect: (Int) -> Unit,
    onMarkSeason: (Int, Boolean) -> Unit,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 10.dp),
    ) {
        items(detail.seasons, key = { it.number }) { season ->
            val active = season.number == selected
            val watched = progress?.watchedIn(season.number) ?: 0
            val complete = watched >= season.episodeCount && season.episodeCount > 0
            Column(
                Modifier
                    .clip(CvShape.Medium)
                    .background(if (active) colors.text.copy(alpha = 0.12f) else colors.glass)
                    .border(
                        1.dp,
                        if (active) colors.text.copy(alpha = 0.3f) else colors.hairline,
                        CvShape.Medium,
                    )
                    .combinedClickable(
                        onClick = { haptics?.play(Haptic.Select); onSelect(season.number) },
                        onLongClick = {
                            haptics?.play(Haptic.Peek)
                            onMarkSeason(season.number, !complete)
                        },
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(
                    "Season ${season.number}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) colors.text else colors.text2,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (complete) {
                        Icon(
                            Icons.Rounded.Check,
                            null,
                            tint = colors.green,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                    }
                    Text(
                        "$watched / ${season.episodeCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (complete) colors.green else colors.text3,
                    )
                }
            }
        }
    }
}

// ---------- About ----------

fun LazyListScope.aboutSection(
    detail: TitleDetail,
    onPerson: (Person) -> Unit,
    onOpen: (MediaItem) -> Unit,
) {
    if (detail.providers.isNotEmpty()) {
        item(key = "providers") { Providers(detail) }
    }
    if (detail.cast.isNotEmpty()) {
        item(key = "cast") { CastRow(detail.cast, onPerson) }
    }
    item(key = "facts") { Facts(detail) }
}

@Composable
private fun Providers(detail: TitleDetail) {
    val colors = CvTheme.colors
    Column(Modifier.padding(top = 10.dp, bottom = 18.dp)) {
        SectionHeader("Where to watch")
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(detail.providers, key = { it.id }) { provider ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(64.dp),
                ) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CvShape.Medium)
                            .border(1.dp, colors.hairline, CvShape.Medium)
                    ) {
                        CvImage(Img.provider(provider.logoPath), provider.name, Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        provider.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.text3,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CastRow(cast: List<Person>, onPerson: (Person) -> Unit) {
    val colors = CvTheme.colors
    Column(Modifier.padding(bottom = 18.dp)) {
        SectionHeader("Cast")
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(cast.take(24), key = { it.id }) { person ->
                Column(
                    Modifier
                        .width(84.dp)
                        .clickableNoRipple { onPerson(person) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(78.dp)
                            .clip(CircleShape)
                            .background(colors.surface2)
                    ) {
                        CvImage(Img.profile(person.profilePath), person.name, Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        person.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    if (!person.character.isNullOrBlank()) {
                        Text(
                            person.character,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.text3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Facts(detail: TitleDetail) {
    val colors = CvTheme.colors
    val rows = buildList {
        detail.status.takeIf { it.isNotBlank() }?.let { add("Status" to it) }
        detail.director?.name?.let { add((if (detail.isSeries) "Creator" else "Director") to it) }
        detail.networks.firstOrNull()?.let { add("Network" to it) }
        detail.companies.firstOrNull()?.let { add("Studio" to it) }
        detail.countries.firstOrNull()?.let { add("Country" to it) }
        detail.spokenLanguages.firstOrNull()?.let { add("Language" to it) }
        if (detail.isSeries && detail.numberOfEpisodes > 0) {
            add("Episodes" to detail.numberOfEpisodes.toString())
        }
        if (detail.voteCount > 0) add("Votes" to "%,d".format(detail.voteCount))
        if (detail.budget > 0) add("Budget" to money(detail.budget))
        if (detail.revenue > 0) add("Box office" to money(detail.revenue))
    }
    if (rows.isEmpty()) return
    Column(Modifier.padding(horizontal = ScreenPadding, vertical = 6.dp)) {
        Text("DETAILS", style = KickerStyle, color = colors.text3)
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(CvShape.XLarge)
                .background(colors.glass)
                .border(1.dp, colors.hairline, CvShape.XLarge)
        ) {
            rows.forEachIndexed { index, (label, value) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.text3,
                        modifier = Modifier.width(104.dp),
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (index < rows.lastIndex) {
                    Box(
                        Modifier
                            .padding(start = 16.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.hairline)
                    )
                }
            }
        }
    }
}

private fun money(value: Long): String = when {
    value >= 1_000_000_000 -> "$%.2fB".format(value / 1_000_000_000.0)
    value >= 1_000_000 -> "$%.1fM".format(value / 1_000_000.0)
    else -> "$%,d".format(value)
}
