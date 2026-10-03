package com.cineverse.app.feature.franchise

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cineverse.app.AppContainer
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.design.tabular
import com.cineverse.app.core.ui.CountUpText
import com.cineverse.app.core.ui.CvButton
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.Img
import com.cineverse.app.core.ui.ProgressRing
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.core.ui.glass
import com.cineverse.app.core.ui.rememberArrival
import com.cineverse.app.core.ui.shimmer
import com.cineverse.app.data.boxoffice.FranchiseMoney
import com.cineverse.app.data.boxoffice.Money
import com.cineverse.app.data.franchise.CollectionInfo
import com.cineverse.app.data.franchise.Franchises
import com.cineverse.app.data.franchise.formatMinutes
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.feature.detail.ExpandableText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One collection, whole: the running order with your place in it, how far
 * through you are, what a finish would cost, and what the series made.
 *
 * The route existed and nothing drew it; "Part of the Alien Collection" on a
 * title page had nowhere to go. It goes here now.
 */
@Composable
fun CollectionScreen(
    viewModel: CollectionViewModel,
    onOpen: (MediaItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val colors = CvTheme.colors
    val listState = rememberLazyListState()
    val info = state.info

    Box(modifier.fillMaxSize().background(colors.ink)) {
        if (info == null) {
            if (state.failed) {
                Column(
                    Modifier.fillMaxSize().padding(36.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Could not reach TMDB", style = MaterialTheme.typography.titleLarge, color = colors.text)
                    Spacer(Modifier.height(16.dp))
                    CvButton("Try again", viewModel::load)
                }
            } else {
                Box(Modifier.fillMaxSize().shimmer())
            }
        } else {
            val watched = Franchises.watchedFilmIds(library)
            val progress = Franchises.progress(info.parts, watched)
            val ordered = Franchises.releaseOrdered(info.parts)
            val gaps = Franchises.gaps(ordered, progress.seenIds)
            val left = Franchises.remainingMinutes(progress, Franchises.runtimes(library))
            // The backdrop drifts up at half the scroll speed, which is the
            // whole of the depth cue a header like this needs.
            val drift by remember {
                derivedStateOf {
                    if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset * 0.5f else 0f
                }
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                item(key = "header") {
                    Box(Modifier.fillMaxWidth().height(300.dp)) {
                        CvImage(
                            Img.backdrop(info.backdrop ?: ordered.firstNotNullOfOrNull { it.backdrop }),
                            null,
                            Modifier
                                .matchParentSize()
                                .graphicsLayer { translationY = drift },
                        )
                        Box(
                            Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        0f to colors.ink.copy(alpha = 0.25f),
                                        0.55f to colors.ink.copy(alpha = 0.55f),
                                        1f to colors.ink,
                                    )
                                )
                        )
                        Row(
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = ScreenPadding, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("COLLECTION", style = KickerStyle, color = colors.text3)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    info.name,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = colors.text,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (progress.label.isNotBlank()) {
                                    Text(progress.label, style = MaterialTheme.typography.labelLarge, color = colors.text2)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            ProgressRing(progress.fraction, size = 82.dp, stroke = 7.dp, delayMillis = 200) {
                                Text(
                                    "${progress.percent}%",
                                    style = MaterialTheme.typography.titleMedium.tabular(),
                                    color = if (progress.complete) colors.gold else colors.text,
                                )
                            }
                        }
                    }
                }

                item(key = "facts") {
                    Column(Modifier.padding(horizontal = ScreenPadding)) {
                        val facts = buildList {
                            add("${progress.released} released")
                            if (progress.upcoming > 0) add("${progress.upcoming} still to come")
                            if (gaps.isNotEmpty()) add("${gaps.size} skipped")
                            if (left > 0) add("about ${formatMinutes(left)} left")
                        }
                        Text(
                            facts.joinToString("  ·  "),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.text3,
                        )
                        progress.nextUp?.let { next ->
                            Spacer(Modifier.height(16.dp))
                            CvButton(
                                if (progress.seen == 0) "Start with ${next.title}" else "Carry on with ${next.title}",
                                { onOpen(next.asItem()) },
                                icon = Icons.Rounded.PlayArrow,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (info.overview.isNotBlank()) {
                            Spacer(Modifier.height(18.dp))
                            ExpandableText(info.overview, collapsedLines = 3)
                        }
                    }
                }

                item(key = "order") {
                    Column(Modifier.padding(horizontal = ScreenPadding).padding(top = 26.dp)) {
                        Text("RUNNING ORDER", style = KickerStyle, color = colors.text3)
                        Spacer(Modifier.height(10.dp))
                        RunningOrder(ordered, progress, gaps.map { it.id }.toSet(), onOpen)
                    }
                }

                state.money?.takeIf { it.reported > 0 }?.let { money ->
                    item(key = "money") {
                        RevenueTimeline(
                            money = money,
                            onOpen = onOpen,
                            modifier = Modifier.padding(horizontal = ScreenPadding).padding(top = 28.dp),
                        )
                    }
                }
            }
        }

        // Once the header has scrolled away, the running order would otherwise
        // slide straight under the clock.
        val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
        val scrim by androidx.compose.animation.core.animateFloatAsState(
            if (scrolled) 1f else 0f, label = "scrim",
        )
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(58.dp)
                .graphicsLayer { alpha = scrim }
                .background(Brush.verticalGradient(listOf(colors.ink, colors.ink.copy(alpha = 0f))))
        )
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = scrim }
                .background(colors.ink)
                .windowInsetsPadding(WindowInsets.statusBars)
        )
        Box(
            Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(8.dp)
                .size(42.dp)
                .clip(CvShape.Circle)
                .background(colors.ink.copy(alpha = 0.6f))
                .clickableNoRipple(onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = colors.text)
        }
    }
}

/**
 * What the series made, film by film: a bar per entry rising in turn, so the
 * shape of a franchise — the peak, the slump, the comeback — is the first
 * thing you read.
 */
@Composable
fun RevenueTimeline(
    money: FranchiseMoney,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CvTheme.colors
    val top = money.films.maxOf { it.revenue }.coerceAtLeast(1)
    Column(modifier.fillMaxWidth().glass(CvShape.XLarge, strength = 0.7f).padding(18.dp)) {
        Text("WORLDWIDE BOX OFFICE", style = KickerStyle, color = colors.text3)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            CountUpText(
                money.revenue,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.gold,
                format = { Money.usd(it) },
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "${money.coverage}% of films reported",
                style = MaterialTheme.typography.labelSmall,
                color = colors.text3,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().height(150.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            money.films.forEachIndexed { index, film ->
                val share = film.revenue.toFloat() / top
                val grown = rememberArrival(if (film.revenue > 0) share.coerceAtLeast(0.04f) else 0.02f, index * 70)
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickableNoRipple { onOpen(film.asItem()) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .fillMaxHeight(grown)
                            .clip(CvShape.Tiny)
                            .background(
                                if (film == money.topFilm) Brush.verticalGradient(listOf(colors.gold, Palette.Gold2))
                                else Brush.verticalGradient(listOf(Palette.Red2, Palette.Red.copy(alpha = 0.6f)))
                            )
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        film.year.takeLast(2).let { if (it.isBlank()) "—" else "’$it" },
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = colors.text3,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
        money.topFilm?.let { best ->
            Spacer(Modifier.height(12.dp))
            Text(
                "Biggest: ${best.title} · ${Money.usd(best.revenue)}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------- state ----------

data class CollectionState(
    val info: CollectionInfo? = null,
    val money: FranchiseMoney? = null,
    val failed: Boolean = false,
)

class CollectionViewModel(private val app: AppContainer, private val id: Int) : ViewModel() {

    private val _state = MutableStateFlow(CollectionState())
    val state: StateFlow<CollectionState> = _state.asStateFlow()

    val library: StateFlow<Library> = app.library.library

    init { load() }

    fun load() {
        _state.value = _state.value.copy(failed = false)
        viewModelScope.launch {
            val info = app.tmdb.collectionInfo(id)
            _state.value = _state.value.copy(info = info, failed = info == null)
            // The money arrives second: it is a request per film, and the
            // running order should never wait on it.
            if (info != null) {
                val money = app.boxOffice.collection(id)
                _state.value = _state.value.copy(money = money)
            }
        }
    }
}
