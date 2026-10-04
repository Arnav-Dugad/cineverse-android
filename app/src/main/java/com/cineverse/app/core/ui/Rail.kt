package com.cineverse.app.core.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.Motion
import com.cineverse.app.data.model.MediaItem

/** The gutter every screen's content lines up to. */
val ScreenPadding = 18.dp

/**
 * How much room the bottom bar needs under a scrolling list.
 *
 * 44dp of fade, 80dp of bar and the gesture inset under that. The first guess
 * was 120dp, which left the last row's captions tucked behind the labels — a
 * list that cannot be scrolled to its own end is a list with a bug in it.
 */
val BottomBarSpace = 156.dp

/**
 * With "Keep the navigation bar pinned" on, how tall that bar is over a page
 * pushed on top of a tab - zero otherwise. Pages scroll under the bar as tabs
 * do (their bottom space already allows for it); only something floating at
 * the bottom edge, like a back-to-top button, rises above it by this much.
 */
val LocalPinnedBarLift = androidx.compose.runtime.staticCompositionLocalOf { 0.dp }

/**
 * A rail's heading.
 *
 * A kicker above it when there is something to say about where the row came from
 * ("Because you watched Severance"), and a "See all" only when there is more
 * behind it than the rail can hold — an affordance that leads nowhere is worse
 * than none.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    count: Int? = null,
    /**
     * The title treatment of whatever this row was derived from.
     *
     * When there is one it REPLACES the typeset title: "Because you're
     * watching" over the Modern Family logo says the same thing in the show's
     * own voice, and printing both would be saying it twice.
     */
    titleLogo: String? = null,
    /** What the action on the right says. */
    actionLabel: String = "See all",
    /** A person's face before the title, for "Starring" and "From" rails. */
    face: String? = null,
    /** A row Gemini wrote: a small sparkle before its title says so. */
    gemini: Boolean = false,
    /** Tapping the title's logo opens that title; the logo flies into its page. */
    onLogo: (() -> Unit)? = null,
    onSeeAll: (() -> Unit)? = null,
) {
    val colors = CvTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            // No sub-heading line above rail titles any more: one line per
            // rail reads cleaner. A rail derived from a title says so on the
            // SAME line - "More like" before that title's own logo.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (gemini) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Rounded.AutoAwesome,
                        contentDescription = "Written by Gemini",
                        tint = GeminiColors[1],
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                if (face != null) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(com.cineverse.app.core.design.CvShape.Circle)
                            .background(colors.surface2)
                            .border(1.5.dp, colors.text.copy(alpha = 0.18f), com.cineverse.app.core.design.CvShape.Circle),
                    ) {
                        CvImage(Img.profile(face), null, Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(10.dp))
                }
                if (titleLogo != null && kicker != null) {
                    Text(
                        "$kicker ",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.text,
                        maxLines = 1,
                    )
                }
                if (titleLogo != null) {
                    TonedLogo(
                        titleLogo,
                        title,
                        Modifier
                            .sharedLogo(titleLogo)
                            .then(
                                if (onLogo != null) Modifier.clickableNoRipple { LogoMorph.pending = titleLogo; onLogo() }
                                else Modifier
                            )
                            .height(34.dp)
                            .widthIn(max = 190.dp),
                        align = Alignment.CenterStart,
                    )
                } else {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = colors.text)
                }
                if (count != null && count > 0) {
                    Text(
                        "  $count",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.text3,
                    )
                }
            }
        }
        if (onSeeAll != null) {
            Row(
                Modifier
                    .clip(com.cineverse.app.core.design.CvShape.Pill)
                    .clickableNoRipple { onSeeAll() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium, color = colors.text2)
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = colors.text2,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/**
 * A horizontal row of posters.
 *
 * The one piece of motion here is the entrance: on a rail's first appearance the
 * cards rise and fade in 40 ms apart, left to right, which reads as the row
 * dealing itself out. It happens once — scrolling back to a rail you have
 * already seen must not replay it, because then it is decoration rather than an
 * arrival.
 */
@Composable
fun PosterRail(
    items: List<MediaItem>,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    kicker: String? = null,
    titleLogo: String? = null,
    face: String? = null,
    /** A row Gemini wrote. */
    gemini: Boolean = false,
    /** For a "More like" row: open the title its logo names. */
    onTitleLogo: (() -> Unit)? = null,
    /** A Top 10: the position is drawn behind each card. */
    numbered: Boolean = false,
    onSeeAll: (() -> Unit)? = null,
    cardWidth: Dp = railCardWidth(),
    showCaption: Boolean = true,
    isWatched: (MediaItem) -> Boolean = { false },
    isSaved: (MediaItem) -> Boolean = { false },
    ratingOf: (MediaItem) -> Int = { 0 },
    matchOf: (MediaItem) -> Int = { 0 },
    onLongPress: ((MediaItem) -> Unit)? = null,
    loading: Boolean = false,
) {
    if (!loading && items.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            SectionHeader(title, kicker = kicker, titleLogo = titleLogo, face = face, onSeeAll = onSeeAll, gemini = gemini, onLogo = onTitleLogo)
            Spacer(Modifier.height(12.dp))
        }
        if (loading) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(6) { PosterSkeleton(width = cardWidth, showCaption = showCaption) }
            }
        } else {
            var revealed by remember(items.firstOrNull()?.key) { mutableStateOf(false) }
            LaunchedEffect(items.firstOrNull()?.key) { revealed = true }
            // Scroll position is remembered per rail: coming back from a title
            // page to find the row you were half way along back at its start is
            // the single most irritating thing a rail can do.
            val state = rememberSaveable(title ?: "rail", saver = LazyListState.Saver) {
                LazyListState()
            }
            // The row is wrapped so the fade at its right-hand edge can sit
            // over it. The fade is a background only - it has no pointer input,
            // so it is not a hit target and the card beneath it still takes the
            // tap, which is the whole reason it can be allowed to cover one.
            Box {
                LazyRow(
                    state = state,
                    contentPadding = PaddingValues(horizontal = ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
                        if (numbered) {
                            // A chart position, then the poster laid over it.
                            TopTenCard(rank = index + 1, cardWidth = cardWidth) { slot ->
                                PosterCard(
                                    item = item,
                                    onOpen = onOpen,
                                    width = cardWidth,
                                    showCaption = showCaption,
                                    watched = isWatched(item),
                                    saved = isSaved(item),
                                    rating = ratingOf(item),
                                    matchPercent = matchOf(item),
                                    onLongPress = onLongPress,
                                    modifier = slot.dealIn(index, revealed).railDepth(),
                                )
                            }
                            return@itemsIndexed
                        }
                        PosterCard(
                            item = item,
                            onOpen = onOpen,
                            width = cardWidth,
                            showCaption = showCaption,
                            watched = isWatched(item),
                            saved = isSaved(item),
                            rating = ratingOf(item),
                            matchPercent = matchOf(item),
                            onLongPress = onLongPress,
                            modifier = Modifier.dealIn(index, revealed).railDepth(),
                        )
                    }
                }
                EdgeHint(state)
                StartEdgeHint(state)
            }
            RailNudge(state, id = title ?: "rail", ready = revealed && items.size > 3)
        }
    }
}

/** A tap with no ripple — used where the whole surface already responds. */
@Composable
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)
