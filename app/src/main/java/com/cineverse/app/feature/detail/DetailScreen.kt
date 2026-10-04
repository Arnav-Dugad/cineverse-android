package com.cineverse.app.feature.detail

import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import com.cineverse.app.feature.sheets.RatingSheet
import com.cineverse.app.feature.sheets.ProgressSheet
import com.cineverse.app.feature.sheets.ListSheet
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Motion
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.CvLogo
import com.cineverse.app.core.ui.Img
import com.cineverse.app.core.ui.PosterRail
import com.cineverse.app.core.ui.ScoreRow
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.SectionHeader
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.core.ui.shimmer
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.Person
import com.cineverse.app.data.model.TitleDetail

/**
 * A title.
 *
 * The website stacks fourteen blocks; on a phone that puts the episode list four
 * thousand pixels down. Here the page is a collapsing hero, one action row, and
 * three segments — **Episodes / About / More like this** — with Episodes
 * selected by default on a series, so the thing people came for is visible
 * without a single scroll.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    spoilerShield: Boolean = false,
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onPerson: (Person) -> Unit,
    onPlayTrailer: (String, String) -> Unit,
    onShare: (TitleDetail) -> Unit,
    modifier: Modifier = Modifier,
    onCollection: (Int) -> Unit = {},
    onBrand: (com.cineverse.app.data.model.Brand) -> Unit = {},
    onService: (id: Int, name: String, logo: String) -> Unit = { _, _, _ -> },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val collection by viewModel.collection.collectAsStateWithLifecycle()
    val exactAir by viewModel.exactAir.collectAsStateWithLifecycle()
    val castHours by viewModel.castHours.collectAsStateWithLifecycle()
    val pitch by viewModel.pitch.collectAsStateWithLifecycle()
    val chat by viewModel.chat.collectAsStateWithLifecycle()
    val nextStill by viewModel.nextStill.collectAsStateWithLifecycle()
    val seasonScores by viewModel.seasonScores.collectAsStateWithLifecycle()
    val places by viewModel.places.collectAsStateWithLifecycle()
    val compare by viewModel.compare.collectAsStateWithLifecycle()
    val trivia by viewModel.trivia.collectAsStateWithLifecycle()
    val watching by viewModel.watchingNow.collectAsStateWithLifecycle()
    var asking by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val library by viewModel.library.collectAsStateWithLifecycle()
    val shows by viewModel.progressFlow.collectAsStateWithLifecycle()
    val colors = CvTheme.colors
    val listState = rememberLazyListState()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val onWifi by com.cineverse.app.core.net.rememberUnmetered()
    // Which sheet, if any, is up. Held here rather than in the view model: it is
    // screen state, it should not survive a process death, and a sheet that
    // reopens itself after the app was killed is a small haunting.
    var sheet by remember { mutableStateOf(TitleSheet.None) }

    val detail = state.detail
    if (detail == null) {
        if (state.loading) {
            DetailSkeleton(viewModel.key, modifier)
            return
        }
        Box(modifier.fillMaxSize().background(colors.ink), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    state.error ?: "Could not load this title.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.text2,
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = { viewModel.load() }, shape = CvShape.Pill) { Text("Try again") }
            }
        }
        return
    }

    val progress = shows[detail.id]

    // How far the hero has scrolled away, 0..1 — drives the app bar's arrival.
    val collapsed by remember {
        derivedStateOf {
            // Full opacity well before the hero has finished leaving, because the
            // moment ANY content slides under the bar the bar has to be a
            // surface rather than a tint — at 700f the overview and the action
            // row were both legible straight through the title.
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 260f).coerceIn(0f, 1f)
        }
    }

    val accent by com.cineverse.app.core.ui.rememberPosterAccent(detail.posterPath, settings.titleColour)
    // The title's colour as a TINT of the page: the hero's artwork (and its
    // trailer) dissolves into this, and the block beneath carries it on and
    // fades it back to the page colour. Both scroll together, so the colour
    // travels with the content and there is never a seam between them.
    val tint by androidx.compose.animation.animateColorAsState(
        accent?.let { androidx.compose.ui.graphics.lerp(colors.ink, it, if (colors.isDark) 0.22f else 0.14f) } ?: colors.ink,
        androidx.compose.animation.core.tween(900),
        label = "tint",
    )
    androidx.compose.runtime.CompositionLocalProvider(com.cineverse.app.core.ui.LocalTitleAccent provides accent) {
    Box(
        modifier
            .fillMaxSize()
            .background(colors.ink)
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 140.dp),
        ) {
            item(key = "hero") {
                DetailHero(
                    tint = tint,
                    detail = detail,
                    collapsed = collapsed,
                    autoplay = settings.autoplay &&
                        (!settings.autoplayOnWifiOnly || onWifi) &&
                        collapsed < 0.4f,
                )
            }

            item(key = "head") {
                Box(
                    Modifier.background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to tint,
                            0.55f to androidx.compose.ui.graphics.lerp(tint, colors.ink, 0.6f),
                            1f to colors.ink,
                        )
                    )
                ) {
                DetailHead(
                    detail = detail,
                    scores = state.scores,
                    saved = library.isSaved(detail.key),
                    watched = library.isWatched(detail.key),
                    myRating = library.ratingOf(detail.key),
                    movieMinutes = library.movieProgress[detail.id]?.minutes ?: 0,
                    showTitleLogo = settings.titleLogos,
                    hideTitle = settings.hideTitle,
                    onSave = viewModel::toggleSaved,
                    onWatched = viewModel::toggleWatched,
                    onRate = { sheet = TitleSheet.Rate },
                    onShare = { onShare(detail) },
                    onPlayTrailer = {
                        detail.trailer?.key?.let { key -> onPlayTrailer(key, detail.title) }
                    },
                    onLists = { sheet = TitleSheet.Lists },
                    onProgress = { sheet = TitleSheet.Progress },
                    onAsk = if (settings.geminiOn) ({ asking = true }) else null,
                    onWatchingNow = if (!detail.isSeries && detail.runtime > 0) viewModel::toggleWatchingNow else null,
                    watchingNow = watching?.id == detail.id,
                )
                }
            }

            // The ending, explained - locked until it is all watched.
            if (settings.geminiOn && (detail.isSeries || detail.releaseDate.take(10) <= java.time.LocalDate.now().toString())) {
                item(key = "ending") {
                    val unlocked = androidx.compose.runtime.remember(detail.key, library.watched.size, progress?.watchedCount) {
                        viewModel.endingUnlocked()
                    }
                    EndingCard(
                        series = detail.isSeries,
                        unlocked = unlocked,
                        season = androidx.compose.runtime.remember(detail.key, progress?.watchedCount) { viewModel.endingSeason() },
                        modifier = Modifier.padding(horizontal = 18.dp).padding(top = 16.dp),
                    ) {
                        asking = true
                        viewModel.explainEnding()
                    }
                }
                item(key = "trivia") {
                    val unlocked = androidx.compose.runtime.remember(detail.key, library.watched.size, progress?.watchedCount) {
                        viewModel.endingUnlocked()
                    }
                    TriviaCard(
                        series = detail.isSeries,
                        running = androidx.compose.runtime.remember(detail.key, progress?.watchedCount) { viewModel.endingSeason() != null },
                        unlocked = unlocked,
                        state = trivia,
                        onLoad = viewModel::loadTrivia,
                        modifier = Modifier.padding(horizontal = 18.dp).padding(top = 10.dp),
                    )
                }
                item(key = "compare") {
                    CompareCard(Modifier.padding(horizontal = 18.dp).padding(top = 10.dp), onOpen = viewModel::openCompare)
                }
            }

            // Directly under the actions and above the tabs, so it is on screen
            // whichever tab is selected. It used to live inside About, which is
            // two taps and a scroll from the question it answers.
            // Only once it has been watched: there is no such thing as a
            // rewatch of something unseen.
            library.watched[detail.key]?.let { entry ->
                item(key = "rewatch") {
                    RewatchPanel(
                        plays = entry.plays,
                        lastWatched = entry.lastPlay,
                        onLogRewatch = viewModel::logRewatch,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
            }

            // Only when TMDB has a real date. Counting down to a guess is worse
            // than saying nothing.
            detail.nextEpisode
                ?.takeIf { settings.countdowns && it.airDate.isNotBlank() }
                ?.let { next ->
                item(key = "next") {
                    Column {
                        NextEpisodePanel(
                            next,
                            Modifier.padding(top = 16.dp),
                            exactAt = exactAir,
                            // The episode's own still; TVmaze's when TMDB has none
                            // yet; the show's artwork as a last resort.
                            still = com.cineverse.app.core.ui.Img.still(next.stillPath) ?: nextStill
                                ?: com.cineverse.app.core.ui.Img.still(detail.backdropPath),
                        )
                        // A reminder you can see: this countdown on the home screen.
                        val context = androidx.compose.ui.platform.LocalContext.current
                        if (androidx.compose.runtime.remember { com.cineverse.app.widget.ShowCountdowns.canPin(context) }) {
                            Text(
                                "Add countdown to Home screen",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.text2,
                                modifier = Modifier
                                    .padding(horizontal = 18.dp)
                                    .padding(top = 10.dp)
                                    .clip(com.cineverse.app.core.design.CvShape.Pill)
                                    .border(1.dp, colors.hairline, com.cineverse.app.core.design.CvShape.Pill)
                                    .clickableNoRipple { com.cineverse.app.widget.ShowCountdowns.pin(context, detail.id) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            pitch?.takeIf { settings.geminiOn }?.let { forYou ->
                item(key = "forYou") {
                    ForYouCard(forYou, Modifier.padding(horizontal = 18.dp).padding(top = 18.dp))
                }
            }

            item(key = "providers") {
                WhereToWatch(detail, onService = { onService(it.id, it.name, it.logoPath.orEmpty()) }, mine = settings.myServices)
            }

            collection?.takeIf { it.id == detail.collectionId }?.let { info ->
                item(key = "collection") {
                    com.cineverse.app.feature.franchise.CollectionBanner(
                        info = info,
                        library = library,
                        currentId = detail.id,
                        onClick = { onCollection(info.id) },
                        modifier = Modifier.padding(horizontal = 18.dp).padding(top = 18.dp),
                    )
                }
            }

            stickyHeader(key = "tabs") {
                SegmentedTabs(
                    tabs = tabsFor(detail),
                    selected = state.tab,
                    onSelect = viewModel::selectTab,
                )
            }

            when (state.tab) {
                DetailTab.Episodes -> episodesSection(
                    state = state,
                    progress = progress,
                    spoilerShield = spoilerShield,
                    swipeToCatchUp = settings.episodeSwipe,
                    onSeason = viewModel::selectSeason,
                    onToggle = viewModel::toggleEpisode,
                    onMarkUpTo = viewModel::markUpTo,
                    onSeasonWatched = viewModel::setSeasonWatched,
                    onHeatmapToggle = viewModel::toggleHeatmap,
                    onHeatMode = viewModel::setHeatMode,
                    onNumbers = viewModel::toggleNumbers,
                    onSeasonRecap = { viewModel.openSeasonRecap(it) },
                    onSeriesRecap = { viewModel.openSeriesRecap() },
                    onPreviously = { viewModel.loadPreviously() },
                    catchUpSeason = if (settings.geminiOn) viewModel.catchUpSeason(progress) else null,
                    onCatchUp = viewModel::loadCatchUp,
                    onOpenEpisode = { season, episode ->
                        viewModel.selectSeason(season)
                        viewModel.selectTab(DetailTab.Episodes)
                    },
                    seasonScores = seasonScores,
                    onRateEpisode = { season, episode, score -> viewModel.rateEpisode(season, episode, score) },
                )

                DetailTab.About -> aboutSection(
                    detail = detail,
                    awards = state.awards,
                    onPerson = onPerson,
                    onOpen = onOpen,
                    onBrand = onBrand,
                    castHours = castHours,
                    places = places,
                )

                DetailTab.More -> item(key = "more") {
                    Column(Modifier.padding(top = 16.dp)) {
                        PosterRail(
                            items = detail.recommendations,
                            onOpen = onOpen,
                            isWatched = { library.isWatched(it.key) },
                            isSaved = { library.isSaved(it.key) },
                        )
                    }
                }
            }
        }

        // The bar arrives only once the hero has gone, carrying the title it
        // replaced — so there is never a moment with no way back and no name.
        DetailAppBar(
            title = detail.title,
            alpha = collapsed,
            onBack = onBack,
        )

        // There is more below this. A title page opens on a poster and a row of
        // buttons, which on a tall phone is the whole first screen and says
        // nothing about the episode list underneath it.
        com.cineverse.app.core.ui.ScrollHint(
            listState = listState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = com.cineverse.app.core.ui.LocalPinnedBarLift.current),
            avoid = "head",
        )
    }
    }

    when (val recap = state.recap) {
        is RecapView.Season -> com.cineverse.app.feature.recap.SeasonRecapSheet(
            title = detail.title,
            backdrop = detail.backdropPath,
            recap = recap.recap,
            onDismiss = viewModel::closeRecap,
        )
        is RecapView.Series -> com.cineverse.app.feature.recap.SeriesFinaleSheet(
            title = detail.title,
            backdrop = detail.backdropPath,
            recap = recap.recap,
            celebrate = settings.confetti,
            onDismiss = viewModel::closeRecap,
        )
        null -> Unit
    }

    compare?.takeIf { settings.geminiOn }?.let { current ->
        state.detail?.let { detail ->
            CompareSheet(
                detail = detail,
                state = current,
                search = viewModel::searchTitles,
                onPick = viewModel::compareWith,
                onAgain = viewModel::openCompare,
                onDismiss = viewModel::closeCompare,
            )
        }
    }

    if (asking && settings.geminiOn) {
        state.detail?.let { detail ->
            val line = androidx.compose.runtime.remember(detail, progress, library.watched.size) { viewModel.spoilerLine() }
            if (line != null) {
                AskTitleSheet(
                    title = detail.title,
                    line = line,
                    suggestions = androidx.compose.runtime.remember(line) { com.cineverse.app.data.ai.TitleChat.suggestions(detail, line) },
                    turns = chat,
                    onAsk = viewModel::ask,
                    onClear = viewModel::clearChat,
                    resolve = viewModel::resolveMentions,
                    onOpenTitle = { asking = false; onOpen(it) },
                    onPerson = { id -> asking = false; onPerson(com.cineverse.app.data.model.Person(id = id, name = "")) },
                    speak = viewModel::speak,
                    stopSpeaking = viewModel::stopSpeaking,
                    onDismiss = { asking = false },
                )
            }
        }
    }

    when (sheet) {
        TitleSheet.None -> Unit

        TitleSheet.Rate -> RatingSheet(
            title = detail.title,
            current = library.ratingOf(detail.key),
            celebrate = settings.confetti,
            onSave = viewModel::setRating,
            onClear = { viewModel.setRating(0) },
            onDismiss = { sheet = TitleSheet.None },
        )

        TitleSheet.Lists -> ListSheet(
            title = detail.title,
            lists = library.lists,
            membership = library.saved[detail.key]?.lists.orEmpty(),
            onToggle = viewModel::setInList,
            onCreate = viewModel::createList,
            onRename = viewModel::renameList,
            onDelete = viewModel::deleteList,
            onDismiss = { sheet = TitleSheet.None },
        )

        TitleSheet.Progress -> ProgressSheet(
            title = detail.title,
            runtime = detail.runtime,
            current = library.movieProgress[detail.id]?.minutes ?: 0,
            onSave = viewModel::setMovieProgress,
            onFinish = viewModel::finishMovie,
            onClear = viewModel::clearMovieProgress,
            onDismiss = { sheet = TitleSheet.None },
        )
    }
}

/** The sheets a title page can raise. */
private enum class TitleSheet { None, Rate, Lists, Progress }

private fun tabsFor(detail: TitleDetail): List<DetailTab> =
    if (detail.isSeries) listOf(DetailTab.Episodes, DetailTab.About, DetailTab.More)
    else listOf(DetailTab.About, DetailTab.More)

@Composable
private fun DetailHero(
    detail: TitleDetail,
    collapsed: Float,
    autoplay: Boolean,
    /** The page colour the artwork dissolves into - ink, tinted by the poster. */
    tint: Color = CvTheme.colors.ink,
) {
    val colors = CvTheme.colors
    // Clipped: the parallax translates the artwork, which would otherwise paint
    // outside the hero where nothing covers it. It also makes the fractional
    // height exact — 420dp at 2.625 density is 1102.5px, and without a clip the
    // image and its scrim round that opposite ways and leave one bright row.
    Box(
        Modifier
            .fillMaxWidth()
            .height(372.dp)
            .clipToBounds()
    ) {
        CvImage(
            Img.backdrop(detail.backdropPath ?: detail.posterPath),
            detail.title,
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // The artwork drifts up at half the scroll speed, which is
                    // what makes the page feel like it has depth rather than
                    // being a list with a picture at the top.
                    translationY = collapsed * 60f
                    alpha = 1f - collapsed * 0.35f
                },
            contentScale = ContentScale.Crop,
        )
        // The trailer, behind the scrim, once the artwork has had its moment.
        //
        // Ambient: muted, looping, no chrome, and unreachable by touch. It stops
        // the moment the hero is scrolled away, because a video playing behind a
        // page you are no longer looking at is a video draining a battery.
        val trailerKey = detail.trailer?.key
        if (autoplay && !trailerKey.isNullOrBlank() && !CvTheme.reducedMotion) {
            var armed by remember(detail.key) { mutableStateOf(false) }
            var playing by remember(detail.key) { mutableStateOf(false) }
            var settled by remember(detail.key) { mutableStateOf(false) }
            LaunchedEffect(detail.key) {
                kotlinx.coroutines.delay(1_200)
                armed = true
            }
            LaunchedEffect(playing) {
                if (!playing) { settled = false; return@LaunchedEffect }
                kotlinx.coroutines.delay(1_400)
                settled = true
            }
            if (armed) {
                val fade by animateFloatAsState(
                    targetValue = if (settled) 1f else 0f,
                    animationSpec = tween(900, easing = Motion.EaseOut),
                    label = "detailTrailer",
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = fade * (1f - collapsed) }
                ) {
                    com.cineverse.app.feature.trailer.YouTubePlayer(
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
            com.cineverse.app.core.ui.FilmGrain(visible = armed && !settled && collapsed < 0.4f)
        }

        // The artwork dissolves into the page rather than ending on it.
        //
        // The first attempt reached full ink at 84% and left the last 16% flat,
        // which put a visible line across the picture — the same hard edge the
        // website spent a whole batch learning to remove. The ramp starts at
        // the halfway mark now and arrives at the page colour exactly at the
        // bottom, so there is no frame where a sharp image sits on flat ink.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to colors.ink.copy(alpha = 0.55f),
                        0.20f to Color.Transparent,
                        0.48f to Color.Transparent,
                        0.62f to tint.copy(alpha = 0.18f),
                        0.76f to tint.copy(alpha = 0.52f),
                        0.88f to tint.copy(alpha = 0.84f),
                        0.96f to tint.copy(alpha = 0.97f),
                        1f to tint,
                    )
                )
        )

    }
}

@Composable
private fun DetailAppBar(title: String, alpha: Float, onBack: () -> Unit) {
    val colors = CvTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.ink.copy(alpha = alpha))
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp)
            .padding(horizontal = 8.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(40.dp)
                .clip(CircleShape)
                .background(if (alpha > 0.5f) Color.Transparent else Color(0x66000000))
                .clickableNoRipple(onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 52.dp)
                .graphicsLayer { this.alpha = alpha },
        )
    }
}
