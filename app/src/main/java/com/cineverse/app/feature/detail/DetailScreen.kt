package com.cineverse.app.feature.detail

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onPerson: (Person) -> Unit,
    onPlayTrailer: (String) -> Unit,
    onShare: (TitleDetail) -> Unit,
    onRate: (TitleDetail) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val shows by viewModel.progressFlow.collectAsStateWithLifecycle()
    val colors = CvTheme.colors
    val listState = rememberLazyListState()

    val detail = state.detail
    if (detail == null) {
        Box(modifier.fillMaxSize().background(colors.ink), contentAlignment = Alignment.Center) {
            if (state.loading) {
                Box(Modifier.fillMaxSize().shimmer())
            } else {
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
        }
        return
    }

    val progress = shows[detail.id]

    // How far the hero has scrolled away, 0..1 — drives the app bar's arrival.
    val collapsed by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 700f).coerceIn(0f, 1f)
        }
    }

    Box(modifier.fillMaxSize().background(colors.ink)) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 140.dp),
        ) {
            item(key = "hero") {
                DetailHero(detail = detail, collapsed = collapsed)
            }

            item(key = "head") {
                DetailHead(
                    detail = detail,
                    scores = state.scores,
                    saved = library.isSaved(detail.key),
                    watched = library.isWatched(detail.key),
                    myRating = library.ratingOf(detail.key),
                    onSave = viewModel::toggleSaved,
                    onWatched = viewModel::toggleWatched,
                    onRate = { onRate(detail) },
                    onShare = { onShare(detail) },
                    onPlayTrailer = { detail.trailer?.key?.let(onPlayTrailer) },
                )
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
                    spoilerShield = false,
                    onSeason = viewModel::selectSeason,
                    onToggle = viewModel::toggleEpisode,
                    onMarkUpTo = viewModel::markUpTo,
                    onSeasonWatched = viewModel::setSeasonWatched,
                )

                DetailTab.About -> aboutSection(
                    detail = detail,
                    onPerson = onPerson,
                    onOpen = onOpen,
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
    }
}

private fun tabsFor(detail: TitleDetail): List<DetailTab> =
    if (detail.isSeries) listOf(DetailTab.Episodes, DetailTab.About, DetailTab.More)
    else listOf(DetailTab.About, DetailTab.More)

@Composable
private fun DetailHero(detail: TitleDetail, collapsed: Float) {
    val colors = CvTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(420.dp)
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
        // The artwork dissolves into the page rather than ending on it. The
        // website spent a batch proving that an opaque hero meeting a lit page
        // reads as a hard line; the fix is the same shape here.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to colors.ink.copy(alpha = 0.55f),
                        0.22f to Color.Transparent,
                        0.58f to Color.Transparent,
                        0.84f to colors.ink.copy(alpha = 0.82f),
                        1f to colors.ink,
                    )
                )
        )
        if (detail.logoPath != null) {
            CvImage(
                Img.logo(detail.logoPath),
                detail.title,
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = ScreenPadding, bottom = 18.dp)
                    .height(74.dp)
                    .fillMaxWidth(0.72f)
                    .graphicsLayer { alpha = 1f - collapsed },
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun DetailAppBar(title: String, alpha: Float, onBack: () -> Unit) {
    val colors = CvTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.ink.copy(alpha = alpha * 0.96f))
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
