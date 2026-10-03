package com.cineverse.app.feature.home

import androidx.compose.foundation.layout.aspectRatio
import com.cineverse.app.core.design.tabular
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.abs
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.animation.core.Animatable
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
import com.cineverse.app.core.ui.TonedLogo
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
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpen: (MediaItem) -> Unit,
    onBrowse: (Route) -> Unit,
    onContinue: (ContinueRow) -> Unit,
    onPeek: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    /** True once the hero has scrolled up behind the top bar. */
    onScrolledPastHero: (Boolean) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val onWifi by rememberUnmetered()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var arranging by remember { mutableStateOf(false) }
    if (arranging) {
        ArrangeSheet(
            rows = state.continueWatching,
            onSave = viewModel::arrange,
            onReset = viewModel::resetArrangement,
            onDismiss = { arranging = false },
        )
    }
    val barPx = with(LocalDensity.current) { 110.dp.toPx() }
    val latestScrolled by androidx.compose.runtime.rememberUpdatedState(onScrolledPastHero)

    // The top bar turns from scrim to glass the moment the hero's foot passes
    // under it: from then on it is over posters and text, not artwork.
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            when {
                first == null -> false
                first.index > 0 -> true
                else -> first.offset + first.size < barPx
            }
        }.distinctUntilChanged().collect { latestScrolled(it) }
    }
    // Leaving Home hands the bar back in its resting state.
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { latestScrolled(false) } }

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
                    autoAdvance = settings.heroAutoAdvance,
                    holdMillis = settings.heroSeconds * 1_000L,
                    isSaved = { library.isSaved(it.key) },
                    onOpen = onOpen,
                    onSave = viewModel::toggleSaved,
                    onNeedLogo = viewModel::ensureHeroLogo,
                )
            }

            if (state.continueWatching.isNotEmpty() || state.upNext.isNotEmpty()) {
                item(key = "continue") {
                    ContinueSection(
                        upNext = state.upNext,
                        rows = state.continueWatching,
                        onContinue = onContinue,
                        onOpen = onOpen,
                        onMark = viewModel::markNext,
                        onSnooze = viewModel::snooze,
                        onDismiss = viewModel::dismiss,
                        onArrange = { arranging = true },
                    )
                }
            }

            if (state.returning.isNotEmpty()) {
                item(key = "returning") {
                    ReturningSection(state.returning, onOpen)
                }
            }

            items(state.personal + state.rails, key = { it.id }) { rail ->
                PosterRail(
                    items = rail.items,
                    title = rail.title,
                    kicker = rail.kicker,
                    titleLogo = rail.kickerLogo,
                    numbered = rail.numbered,
                    onOpen = onOpen,
                    onSeeAll = rail.seeAll?.let { route -> { onBrowse(route) } },
                    isWatched = { library.isWatched(it.key) },
                    isSaved = { library.isSaved(it.key) },
                    ratingOf = { library.ratingOf(it.key) },
                    matchOf = { rail.match[it.key] ?: 0 },
                    onLongPress = onPeek,
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
internal fun Hero(
    items: List<MediaItem>,
    loading: Boolean,
    logos: Map<String, String>,
    trailers: Map<String, String>,
    autoplay: Boolean,
    autoAdvance: Boolean,
    holdMillis: Long,
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

    // Is the trailer for the slide now showing actually on screen?
    //
    // Read by the title block as well as the rotation: the type leans out of
    // the way once there is something moving behind it.
    //
    // This drives how long the slide is held, and it had to, because the two
    // numbers were in a race the trailer could not win: the hero advanced every
    // seven seconds while the trailer needed about seven and a half to load,
    // start and fade in. Every trailer was destroyed one frame before it became
    // visible, which looked exactly like autoplay being broken.
    var trailerVisible by remember { mutableStateOf(false) }

    // Advancing is a plain counter so that a swipe can bump it and restart the
    // wait, rather than fighting a timer that does not know it was overruled.
    var advance by remember { mutableIntStateOf(0) }

    LaunchedEffect(items.size, advance, trailerVisible, autoAdvance, holdMillis) {
        if (!autoAdvance) return@LaunchedEffect
        // A slide with a trailer PLAYING holds four times as long. Cutting away
        // from a trailer two seconds after it appears is worse than never having
        // played it.
        kotlinx.coroutines.delay(if (trailerVisible) holdMillis * 4 else holdMillis)
        index = (index + 1) % items.size
        advance++
    }

    val item = items[index.coerceIn(items.indices)]
    val trailerPlaying = trailerVisible

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
    Box(
        Modifier
            .fillMaxWidth()
            .height(540.dp)
            .clipToBounds()
            // Swipe the hero.
            //
            // The dots underneath always implied it, and a carousel that shows
            // position but cannot be driven is a carousel that feels broken. The
            // threshold is accumulated so one long drag moves one slide rather
            // than racing through the whole row, and reaching either end gives
            // the Edge signature instead of silently wrapping — a wrap you
            // did not ask for reads as having lost your place.
            .pointerInput(items.size) {
                var travel = 0f
                detectHorizontalDragGestures(
                    onDragStart = { travel = 0f },
                    onDragCancel = { travel = 0f },
                    // ONE slide per gesture, decided when the finger LIFTS.
                    //
                    // The first version stepped as the finger moved and reset
                    // its counter at every threshold, so a brisk 550px flick
                    // crossed the line six times and jumped six slides. A
                    // carousel moves one card per swipe; that is the whole
                    // contract, and it can only be honoured at the end of the
                    // gesture, when the total distance is known.
                    onDragEnd = {
                        val step = when {
                            travel <= -72f -> 1
                            travel >= 72f -> -1
                            else -> 0
                        }
                        travel = 0f
                        if (step != 0) {
                            haptics?.play(Haptic.Select)
                            index = (index + step + items.size) % items.size
                            // Restart the hold, so a slide you chose is not
                            // whipped away by a timer already part way through.
                            advance++
                        }
                    },
                ) { _, delta -> travel += delta }
            }
    ) {
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
            // A second and a bit of artwork first. Long enough that the app
            // does not start a video the instant it opens, short enough that
            // the trailer is up well inside the slide's own hold.
            LaunchedEffect(item.key) {
                kotlinx.coroutines.delay(1_200)
                armed = true
            }
            // Revealed a beat AFTER the first playing signal, not on it.
            //
            // YouTube shows its control overlay for the first few seconds of an
            // embed and then auto-hides it. Fading in the moment playback starts
            // meant a pause button and two skip arrows drifted into view over
            // the hero and sat there. Waiting for them to go is the difference
            // between an ambient backdrop and a video someone left running.
            var settled by remember(item.key) { mutableStateOf(false) }
            LaunchedEffect(playing) {
                if (!playing) {
                    settled = false
                    return@LaunchedEffect
                }
                kotlinx.coroutines.delay(1_400)
                settled = true
            }
            // Tells the rotation above to wait. Cleared when the slide changes,
            // so a trailer that never loaded cannot pin the hero for ever.
            LaunchedEffect(settled, item.key) { trailerVisible = settled }
            androidx.compose.runtime.DisposableEffect(item.key) {
                onDispose { trailerVisible = false }
            }
            if (armed) {
                val fade by animateFloatAsState(
                    targetValue = if (settled) 1f else 0f,
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
            // The treatment where there is one, the typeset name where there is
            // not. It crossfades rather than popping in, because a logo that
            // arrives a beat after the artwork should look like it was always
            // on its way rather than like a correction.
            //
            // Once the trailer is actually PLAYING the title shrinks and the
            // meta line goes, which is the move every streaming hero makes and
            // for the same reason: the moment there is motion behind the type,
            // the type is in the way of the thing it was advertising. It is a
            // transform and a fade, not a relayout, so nothing below it moves.
            val logo = logos[item.key]?.takeIf { it.isNotBlank() }
            val lean by animateFloatAsState(
                targetValue = if (trailerPlaying) 1f else 0f,
                animationSpec = tween(Motion.Slow, easing = Motion.EaseOut),
                label = "heroLean",
            )
            Crossfade(
                targetState = logo,
                animationSpec = tween(Motion.Slow, easing = Motion.EaseOut),
                label = "heroTitle",
            ) { path ->
                if (path != null) {
                    TonedLogo(
                        path,
                        item.title,
                        Modifier
                            .height(84.dp)
                            .fillMaxWidth(0.78f)
                            .graphicsLayer {
                                val shrink = 1f - 0.34f * lean
                                scaleX = shrink
                                scaleY = shrink
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f)
                            },
                    )
                } else {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer {
                            val shrink = 1f - 0.34f * lean
                            scaleX = shrink
                            scaleY = shrink
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f)
                        },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.graphicsLayer { alpha = 1f - lean },
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
    upNext: List<com.cineverse.app.data.airing.UpNextItem>,
    rows: List<ContinueRow>,
    onContinue: (ContinueRow) -> Unit,
    onOpen: (MediaItem) -> Unit,
    onMark: (ContinueRow) -> Unit,
    onSnooze: (ContinueRow) -> Unit,
    onDismiss: (ContinueRow) -> Unit,
    onArrange: () -> Unit = {},
) {
    // One clock for every countdown in the row, ticking once a second only
    // while a countdown that needs seconds is on screen.
    val live = upNext.any { it.exact && it.at > System.currentTimeMillis() }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(live) {
        while (live) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000L - now % 1_000L)
        }
    }
    // Up Next arrives after the row is already drawn, and a LazyRow keeps the
    // card you were looking at in place when items are inserted before it -
    // so the new cards appeared off the left edge, unseen. If the row is still
    // at its start, it follows them back; a row you scrolled yourself stays put.
    val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
    var touched by remember { mutableStateOf(false) }
    // Only a finger counts as the user scrolling - a drag on the row - never
    // the programmatic scroll below.
    LaunchedEffect(rowState) {
        rowState.interactionSource.interactions.collect { interaction ->
            if (interaction is androidx.compose.foundation.interaction.DragInteraction.Start) touched = true
        }
    }
    LaunchedEffect(upNext.size) {
        if (upNext.isNotEmpty() && !touched) rowState.scrollToItem(0)
    }
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(
            "Continue watching",
            count = rows.size + upNext.size,
            // Only worth offering with something to put in order.
            onSeeAll = if (rows.size >= 2) onArrange else null,
            actionLabel = "Arrange",
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The website's Up Next: shows you are caught up on lead the row,
            // counting down to the episode they are waiting for.
            items(upNext, key = { "up_${it.show.id}" }) { item ->
                UpNextCard(item, now) { onOpen(item.show.asItem()) }
            }
            items(rows, key = { it.item.key }) { row ->
                ContinueCard(
                    row = row,
                    onContinue = onContinue,
                    onOpen = onOpen,
                    onMark = onMark,
                    onSnooze = onSnooze,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

/**
 * A show you are caught up on, and the episode it is waiting for: the still if
 * TMDB has one, a countdown, and what kind of episode it is. It counts to the
 * second when the broadcaster's own time is known, and to the day when only a
 * date is.
 */
@Composable
private fun UpNextCard(item: com.cineverse.app.data.airing.UpNextItem, now: Long, onOpen: () -> Unit) {
    val colors = CvTheme.colors
    val countdown = com.cineverse.app.data.airing.Airing.countdown(item, now)
    val out = countdown == "Out now"
    Column(Modifier.width(248.dp).clickableNoRipple(onOpen)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(CvShape.Large)
                .background(colors.surface2)
        ) {
            CvImage(
                Img.still(item.next.still ?: item.show.backdrop ?: item.show.poster),
                item.show.name,
                Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0x8006060B),
                            0.4f to Color.Transparent,
                            1f to Color(0xCC06060B),
                        )
                    )
            )
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(CvShape.Pill)
                    .background(if (out) Palette.Green.copy(alpha = 0.9f) else Color(0xCC06060B))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!out) {
                    Icon(
                        Icons.Rounded.Schedule, null,
                        tint = Palette.Gold, modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    countdown,
                    style = MaterialTheme.typography.labelMedium.tabular(),
                    color = Color.White,
                    maxLines = 1,
                )
            }
            Text(
                item.kind.uppercase(),
                style = KickerStyle,
                color = if (item.kind == "New episode") Color.White.copy(alpha = 0.8f) else Palette.Gold,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            )
        }
        Text(
            item.show.name,
            style = MaterialTheme.typography.titleSmall,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 9.dp),
        )
        Text(
            buildString {
                append("S${item.next.season} E${item.next.episode}")
                if (item.exact && !out) append("  ·  ${com.cineverse.app.data.airing.Airing.localTime(item.at)}")
                else if (item.next.name.isNotBlank() && !item.next.name.matches(Regex("Episode \\d+"))) {
                    append("  ·  ${item.next.name}")
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = colors.text3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * The website's "Returning this month": shows you finished, back with a new
 * season this calendar month, each badged with when.
 */
@Composable
private fun ReturningSection(items: List<com.cineverse.app.data.airing.ReturningItem>, onOpen: (MediaItem) -> Unit) {
    val colors = CvTheme.colors
    Column(Modifier.fillMaxWidth()) {
        SectionHeader("Returning this month", count = items.size)
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { "ret_${it.show.id}" }) { entry ->
                Column(Modifier.width(132.dp).clickableNoRipple { onOpen(entry.show.asItem()) }) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(CvShape.Large)
                            .background(colors.surface2)
                    ) {
                        CvImage(Img.poster(entry.show.poster), entry.show.name, Modifier.fillMaxSize())
                        Text(
                            entry.badge(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                                .clip(CvShape.Pill)
                                .background(if (entry.out) Palette.Green.copy(alpha = 0.92f) else Palette.Red.copy(alpha = 0.92f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Text(
                        entry.show.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.text2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 7.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(
    row: ContinueRow,
    onContinue: (ContinueRow) -> Unit,
    onOpen: (MediaItem) -> Unit,
    onMark: (ContinueRow) -> Unit,
    onSnooze: (ContinueRow) -> Unit,
    onDismiss: (ContinueRow) -> Unit,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Three gestures on one card, which is two more than a card usually earns.
    // They are here because Continue Watching is the row people touch most and
    // every one of its actions otherwise costs a trip into the title page:
    //
    //   UP    tick the next episode and move on
    //   DOWN  snooze, out of the row until something is ticked again
    //   LEFT  dismiss, drop the show from the row entirely
    //
    // The axis is decided by whichever way the finger committed first and
    // LOCKED for the rest of the gesture. A card that changes its mind halfway
    // because a thumb drifted is a card that performs the wrong action.
    //
    // And the card has to be HELD first. It used to answer any drag, which
    // meant a scroll of the page that happened to start on a card ticked an
    // episode, and the row itself could not be scrolled from a card at all.
    // A press-and-hold lifts the card off the row; only a lifted card can be
    // thrown. Everything else is a scroll, as a thumb expects.
    val offsetX = remember(row.item.key) { Animatable(0f) }
    val offsetY = remember(row.item.key) { Animatable(0f) }
    var axis by remember(row.item.key) { mutableStateOf<Int?>(null) }
    var armed by remember(row.item.key) { mutableStateOf(false) }
    var lifted by remember(row.item.key) { mutableStateOf(false) }
    // When a lift ended. A plain click fires on release however long the
    // press was, so letting go of a held card opened the show; a release
    // that ends a lift is not a tap.
    var releasedAt by remember(row.item.key) { mutableStateOf(0L) }
    val lift by animateFloatAsState(if (lifted) 1f else 0f, Motion.lively(), label = "lift")
    val trigger = with(density) { 72.dp.toPx() }

    fun reset() {
        scope.launch {
            axis = null
            armed = false
            lifted = false
            launch { offsetX.animateTo(0f, Motion.landing()) }
            launch { offsetY.animateTo(0f, Motion.landing()) }
        }
    }

    Box {
        // What is underneath, revealed by the drag. Dim until the gesture is
        // ARMED: a backing that is bright from the first pixel says something
        // is there, one that brightens at the threshold says let go now, which
        // is the only thing a thumb needs to know mid-drag.
        SwipeBacking(axis, armed, Modifier.matchParentSize())

        Column(
            Modifier
                .width(248.dp)
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                .graphicsLayer {
                    val grow = 1f + 0.045f * lift
                    scaleX = grow
                    scaleY = grow
                    shadowElevation = 18f * lift
                    shape = CvShape.Large
                    clip = false
                }
                // The click goes OUTSIDE the gesture detector. The innermost
                // handler sees a touch first, and `clickable` consumes the
                // down - which, inside it, cancelled the long press before it
                // could fire, so a held card simply opened the show.
                .clickableNoRipple {
                    if (lifted || System.currentTimeMillis() - releasedAt < 400) return@clickableNoRipple
                    onOpen(row.item)
                }
                .pointerInput(row.item.key) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            lifted = true
                            haptics?.play(Haptic.Peek)
                        },
                        onDragEnd = {
                            releasedAt = System.currentTimeMillis()
                            when {
                                axis == AXIS_VERTICAL && offsetY.value <= -trigger -> {
                                    haptics?.play(Haptic.Success); onMark(row)
                                }
                                axis == AXIS_VERTICAL && offsetY.value >= trigger -> {
                                    haptics?.play(Haptic.Drop); onSnooze(row)
                                }
                                axis == AXIS_HORIZONTAL && offsetX.value <= -trigger -> {
                                    haptics?.play(Haptic.Drop); onDismiss(row)
                                }
                            }
                            reset()
                        },
                        onDragCancel = { releasedAt = System.currentTimeMillis(); reset() },
                    ) { change, delta ->
                        change.consume()
                        scope.launch {
                            if (axis == null) {
                                val nextX = offsetX.value + delta.x
                                val nextY = offsetY.value + delta.y
                                if (abs(nextX) + abs(nextY) > 8f) {
                                    axis = if (abs(nextX) > abs(nextY)) AXIS_HORIZONTAL
                                    else AXIS_VERTICAL
                                }
                                offsetX.snapTo(nextX)
                                offsetY.snapTo(nextY)
                                return@launch
                            }
                            // Rubber band past the trigger, so the gesture has
                            // a floor you can feel rather than one you discover.
                            fun damp(value: Float): Float {
                                if (abs(value) <= trigger) return value
                                val over = abs(value) - trigger
                                return (trigger + over * 0.3f) * (if (value < 0) -1f else 1f)
                            }
                            if (axis == AXIS_HORIZONTAL) {
                                offsetX.snapTo(damp(offsetX.value + delta.x).coerceAtMost(0f))
                                offsetY.snapTo(0f)
                            } else {
                                offsetY.snapTo(damp(offsetY.value + delta.y))
                                offsetX.snapTo(0f)
                            }
                            val past = if (axis == AXIS_HORIZONTAL) {
                                abs(offsetX.value) >= trigger
                            } else abs(offsetY.value) >= trigger
                            if (past != armed) {
                                armed = past
                                haptics?.play(if (past) Haptic.Land else Haptic.Detent)
                            }
                        }
                    }
                }
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
                    .clickableNoRipple {
                        if (lifted || System.currentTimeMillis() - releasedAt < 400) return@clickableNoRipple
                        onContinue(row)
                    },
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
            // Lifted and not yet thrown: say what each direction does. Three
            // gestures nobody can see are three gestures nobody uses.
            if (lift > 0.01f && axis == null) {
                GestureHints(row, Modifier.matchParentSize().graphicsLayer { alpha = lift })
            }
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
}

@Composable
private fun GestureHints(row: ContinueRow, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Color(0xD906060B))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        GestureHint(
            Icons.Rounded.ArrowUpward,
            if (row.isMovie) "Mark watched" else "Mark ${row.label} watched",
            Palette.Green2,
        )
        GestureHint(Icons.Rounded.ArrowDownward, "Hide until your next tick", Palette.Gold)
        GestureHint(Icons.AutoMirrored.Rounded.ArrowBack, "Remove from this row", Palette.Red2)
    }
}

@Composable
private fun GestureHint(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1)
    }
}

private const val AXIS_HORIZONTAL = 0
private const val AXIS_VERTICAL = 1

/**
 * The hint behind a card being dragged.
 *
 * Dim until the gesture is ARMED. A backing that is bright from the first pixel
 * says something is there; one that brightens at the threshold says let go now,
 * which is the only thing a thumb needs to know mid-drag.
 */
@Composable
private fun SwipeBacking(axis: Int?, armed: Boolean, modifier: Modifier = Modifier) {
    val colors = CvTheme.colors
    if (axis == null) return
    val glow by animateFloatAsState(
        targetValue = if (armed) 1f else 0.3f,
        animationSpec = Motion.snappy(),
        label = "backing",
    )
    Box(
        modifier
            .clip(CvShape.Large)
            .background(
                if (axis == AXIS_HORIZONTAL) Palette.Red2.copy(alpha = 0.18f * glow)
                else colors.green.copy(alpha = 0.18f * glow)
            )
    )
}
