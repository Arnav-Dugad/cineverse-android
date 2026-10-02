package com.cineverse.app.feature.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import com.cineverse.app.feature.trailer.YouTubePlayer
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Motion
import com.cineverse.app.core.design.Palette
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.net.rememberUnmetered
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.CvLogo
import com.cineverse.app.core.ui.ProgressBar
import com.cineverse.app.core.ui.shimmer
import com.cineverse.app.core.ui.Img
import com.cineverse.app.core.ui.PosterRail
import com.cineverse.app.core.ui.PosterSkeleton
import com.cineverse.app.core.ui.PullToRefresh
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.SectionHeader
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.data.model.ContinueRow
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.nav.Route

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpen: (MediaItem) -> Unit,
    onBrowse: (Route) -> Unit,
    onContinue: (ContinueRow) -> Unit,
    onQuickActions: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val onWifi by rememberUnmetered()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    PullToRefresh(
        refreshing = state.refreshing,
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            item(key = "hero") {
                Hero(
                    items = state.hero,
                    loading = state.loading,
                    logos = state.heroLogos,
                    trailers = state.heroTrailers,
                    autoplay = settings.autoplay && (!settings.autoplayOnWifiOnly || onWifi),
                    isSaved = { library.isSaved(it.key) },
                    onOpen = onOpen,
                    onSave = viewModel::toggleSaved,
                    onNeedLogo = viewModel::ensureHeroLogo,
                )
            }

            if (state.continueWatching.isNotEmpty()) {
                item(key = "continue") {
                    ContinueSection(state.continueWatching, onContinue, onOpen)
                }
            }

            items(state.personal + state.rails, key = { it.id }) { rail ->
                PosterRail(
                    items = rail.items,
                    title = rail.title,
                    kicker = rail.kicker,
                    onOpen = onOpen,
                    onSeeAll = rail.seeAll?.let { route -> { onBrowse(route) } },
                    isWatched = { library.isWatched(it.key) },
                    isSaved = { library.isSaved(it.key) },
                    ratingOf = { library.ratingOf(it.key) },
                    matchOf = { rail.match[it.key] ?: 0 },
                    onLongPress = onQuickActions,
                )
            }

            if (state.loading) {
                items(3) { index ->
                    Column {
                        Box(
                            Modifier
                                .padding(horizontal = ScreenPadding)
                                .width(160.dp)
                                .height(20.dp)
                                .clip(CvShape.Small)
                                .shimmer()
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(
                            Modifier.padding(horizontal = ScreenPadding),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            repeat(3) { PosterSkeleton() }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The hero.
 *
 * One title at a time, full bleed, dissolving to the next every seven seconds
 * with a slow drift across the artwork — the website's Ken Burns, which is the
 * right idea and costs nothing here because it is a single scaling layer on the
 * compositor.
 *
 * The important detail is the bottom edge: the artwork is masked into the page
 * rather than ending on it. The website spent a whole batch learning that an
 * opaque hero meeting a lit page is a hard line, and the fix is the same here —
 * the picture is already half gone by the time the edge arrives.
 */
@Composable
private fun Hero(
    items: List<MediaItem>,
    loading: Boolean,
    logos: Map<String, String>,
    trailers: Map<String, String>,
    autoplay: Boolean,
    isSaved: (MediaItem) -> Boolean,
    onOpen: (MediaItem) -> Unit,
    onSave: (MediaItem) -> Unit,
    onNeedLogo: (MediaItem) -> Unit,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    var index by remember { mutableIntStateOf(0) }

    if (loading || items.isEmpty()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(540.dp)
                .shimmer(active = loading)
        )
        return
    }

    LaunchedEffect(items.size) {
        while (true) {
            kotlinx.coroutines.delay(Motion.HeroHoldMs)
            index = (index + 1) % items.size
        }
    }

    val item = items[index.coerceIn(items.indices)]

    // This slide and the next, so the logo is already there when it turns.
    LaunchedEffect(index, items.size) {
        onNeedLogo(item)
        items.getOrNull((index + 1) % items.size)?.let(onNeedLogo)
    }

    // CLIPPED, and that is not cosmetic: the Ken Burns drift scales the artwork
    // to 1.14, which puts about 99px of it BELOW the hero on a 540dp box, where
    // the scrim does not reach. It painted as a bright band across the page —
    // measured at 188 levels out of 255 — and no amount of gradient could have
    // fixed it, because the overflow was never inside the gradient's box.
    Box(Modifier.fillMaxWidth().height(540.dp).clipToBounds()) {
        Crossfade(
            targetState = item,
            animationSpec = tween(Motion.HeroFadeMs, easing = Motion.EaseOut),
            label = "hero",
        ) { shown ->
            Box(Modifier.fillMaxSize()) {
                val reduced = CvTheme.reducedMotion
                val drift = if (reduced) 1.02f else {
                    val transition = rememberInfiniteTransition(label = "ken")
                    val value by transition.animateFloat(
                        initialValue = 1.04f,
                        targetValue = 1.14f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(16_000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "scale",
                    )
                    value
                }
                CvImage(
                    Img.backdrop(shown.backdropPath ?: shown.posterPath),
                    shown.title,
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { scaleX = drift; scaleY = drift },
                    contentScale = ContentScale.Crop,
                )
            }
        }

        // The trailer, behind everything, after a beat.
        //
        // It plays MUTED and with no controls, and it only becomes visible once
        // YouTube has confirmed it is actually playing -- so a slow connection
        // shows the backdrop for longer rather than showing a black rectangle
        // where the artwork was. The same rule the website's video background
        // follows, and the reason that one never flashes.
        val trailerKey = trailers[item.key]?.takeIf { it.isNotBlank() }
        if (autoplay && trailerKey != null && !CvTheme.reducedMotion) {
            var armed by remember(item.key) { mutableStateOf(false) }
            var playing by remember(item.key) { mutableStateOf(false) }
            // Three seconds of artwork first. Starting a video the instant the
            // app opens is the behaviour people turn autoplay off to escape.
            LaunchedEffect(item.key) {
                kotlinx.coroutines.delay(3_000)
                armed = true
            }
            if (armed) {
                val fade by animateFloatAsState(
                    targetValue = if (playing) 1f else 0f,
                    animationSpec = tween(900, easing = Motion.EaseOut),
                    label = "trailerFade",
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = fade }
                ) {
                    YouTubePlayer(
                        videoKey = trailerKey,
                        modifier = Modifier.fillMaxSize(),
                        muted = true,
                        showControls = false,
                        loop = true,
                        ambient = true,
                        onPlaying = { playing = true },
                    )
                }
            }
        }

        // Two gradients, not one: a short wash under the status bar so the
        // clock stays readable over anything, and a tall one that carries the
        // artwork into the page rather than cutting it off.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to colors.ink.copy(alpha = 0.72f),
                        0.16f to Color.Transparent,
                        0.44f to Color.Transparent,
                        0.58f to colors.ink.copy(alpha = 0.22f),
                        0.72f to colors.ink.copy(alpha = 0.58f),
                        0.86f to colors.ink.copy(alpha = 0.88f),
                        0.95f to colors.ink.copy(alpha = 0.98f),
                        1f to colors.ink,
                    )
                )
        )


        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = ScreenPadding)
                .padding(bottom = 26.dp)
                .fillMaxWidth(),
        ) {
            Text(
                "TRENDING NOW",
                style = KickerStyle,
                color = Palette.Red2,
            )
            Spacer(Modifier.height(8.dp))
            // The treatment where there is one, the typeset name where there is
            // not. It crossfades rather than popping in, because a logo that
            // arrives a beat after the artwork should look like it was always
            // on its way rather than like a correction.
            val logo = logos[item.key]?.takeIf { it.isNotBlank() }
            Crossfade(
                targetState = logo,
                animationSpec = tween(Motion.Slow, easing = Motion.EaseOut),
                label = "heroTitle",
            ) { path ->
                if (path != null) {
                    CvLogo(
                        path,
                        item.title,
                        Modifier
                            .height(84.dp)
                            .fillMaxWidth(0.78f),
                    )
                } else {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (item.voteAverage > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = colors.gold,
                            modifier = Modifier.size(0.dp),
                        )
                        Text(
                            String.format("★ %.1f", item.voteAverage),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.gold,
                        )
                    }
                }
                if (item.year.isNotBlank()) {
                    Text(item.year, style = MaterialTheme.typography.labelLarge, color = Color(0xCCFFFFFF))
                }
                Text(
                    if (item.type == com.cineverse.app.data.model.MediaType.Tv) "Series" else "Film",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xCCFFFFFF),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { haptics?.play(Haptic.Tap); onOpen(item) },
                    shape = CvShape.Pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF06060B),
                    ),
                    modifier = Modifier.height(46.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open", style = MaterialTheme.typography.labelLarge)
                }
                val saved = isSaved(item)
                Button(
                    onClick = {
                        haptics?.play(if (saved) Haptic.Untick else Haptic.Tick)
                        onSave(item)
                    },
                    shape = CvShape.Pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0x33FFFFFF),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.height(46.dp),
                ) {
                    Icon(
                        if (saved) Icons.Rounded.Check else Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (saved) "In your list" else "My List", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(18.dp))
            // Which of the eight you are on, and a tap to jump.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEachIndexed { position, _ ->
                    val active = position == index
                    Box(
                        Modifier
                            .height(3.dp)
                            .width(if (active) 26.dp else 10.dp)
                            .clip(CircleShape)
                            .background(if (active) Palette.Red2 else Color(0x4DFFFFFF))
                            .clickableNoRipple { index = position }
                    )
                }
            }
        }
    }
}

/**
 * Continue Watching: wide cards with the next episode named, a progress bar, and
 * a tick that marks it watched without leaving the screen.
 *
 * This row is the app's reason to exist, so it sits directly under the hero and
 * its tick target is 48dp — the one thing a user does most should be the easiest
 * thing to hit.
 */
@Composable
private fun ContinueSection(
    rows: List<ContinueRow>,
    onContinue: (ContinueRow) -> Unit,
    onOpen: (MediaItem) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader("Continue watching", count = rows.size)
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(rows, key = { it.item.key }) { row ->
                ContinueCard(row, onContinue = onContinue, onOpen = onOpen)
            }
        }
    }
}

@Composable
private fun ContinueCard(
    row: ContinueRow,
    onContinue: (ContinueRow) -> Unit,
    onOpen: (MediaItem) -> Unit,
) {
    val colors = CvTheme.colors
    Column(
        Modifier
            .width(248.dp)
            .clickableNoRipple { onOpen(row.item) }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(CvShape.Large)
                .background(colors.surface2)
        ) {
            CvImage(
                Img.still(row.stillPath ?: row.item.backdropPath ?: row.item.posterPath),
                row.item.title,
                Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color(0xCC06060B),
                        )
                    )
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000))
                    .clickableNoRipple { onContinue(row) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Continue ${row.item.title}",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            ProgressBar(
                row.progress,
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 8.dp),
            )
        }
        Text(
            row.item.title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 9.dp),
        )
        Text(
            buildString {
                append(row.label)
                if (!row.isMovie && row.remaining > 0) append("  ·  ${row.remaining} left")
            },
            style = MaterialTheme.typography.labelSmall,
            color = colors.text3,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
