package com.cineverse.app.feature.boxoffice

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cineverse.app.AppContainer
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.design.tabular
import com.cineverse.app.core.ui.BottomBarSpace
import com.cineverse.app.core.ui.CountUpText
import com.cineverse.app.core.ui.CvChip
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.CvScreenBar
import com.cineverse.app.core.ui.GrowBar
import com.cineverse.app.core.ui.Img
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.core.ui.glass
import com.cineverse.app.core.ui.riseIn
import com.cineverse.app.core.ui.shimmer
import com.cineverse.app.data.boxoffice.FilmMoney
import com.cineverse.app.data.boxoffice.FranchiseMoney
import com.cineverse.app.data.boxoffice.Money
import com.cineverse.app.data.model.MediaItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * The website's /box-office: the highest-grossing films TMDB reports, and the
 * franchises behind them ranked by their whole runs.
 *
 * Every figure is a reported one. The chart is hydrated film by film because
 * TMDB sorts by revenue without saying what the revenue is, and a film with no
 * figure is left out rather than ranked at zero. Indian productions are also
 * shown in crore, converted at today's rate and marked approximate, because
 * that is the unit anyone who follows that box office thinks in.
 */
@Composable
fun BoxOfficeScreen(
    viewModel: BoxOfficeViewModel,
    onOpen: (MediaItem) -> Unit,
    onCollection: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPerson: (Int) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = CvTheme.colors
    val listState = rememberLazyListState()
    val shownFilms = remember { mutableSetOf<Any>() }
    val shownLeague = remember { mutableSetOf<Any>() }
    val shownDirectors = remember { mutableSetOf<Any>() }

    // Pages as you reach the end, like every other grid in the app.
    val atEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(atEnd, state.films.size, state.view) {
        if (atEnd && state.view == BoxView.Films) viewModel.loadMore()
    }

    Column(modifier.fillMaxSize().background(colors.ink)) {
        CvScreenBar("Box Office", onBack)

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = BottomBarSpace),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "hero") { BoxHero(state) }

            item(key = "views") {
                Row(
                    Modifier.padding(horizontal = ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (view in BoxView.entries) {
                        CvChip(view.label, state.view == view, { viewModel.setView(view) })
                    }
                }
            }

            when (state.view) {
                BoxView.Films -> {
                    item(key = "filters") {
                        Row(
                            Modifier.padding(horizontal = ScreenPadding),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            com.cineverse.app.core.ui.CvDropdown(
                                "Decade",
                                Decades.map { it to if (it == 0) "All decades" else "${it}s" },
                                state.decade,
                            ) { viewModel.setDecade(it) }
                            com.cineverse.app.core.ui.CvDropdown(
                                "Sort",
                                BoxSort.entries.map { it to it.label },
                                state.sort,
                                active = true,
                            ) { viewModel.setSort(it) }
                        }
                    }
                    val rows = state.visibleFilms
                    val top = state.films.firstOrNull()?.revenue ?: 1L
                    items(rows.size, key = { "f_${rows[it].second.id}" }) { index ->
                        val (rank, film) = rows[index]
                        FilmRow(
                            rank = rank,
                            film = film,
                            share = film.revenue.toFloat() / top.coerceAtLeast(1),
                            rate = state.rate,
                            onClick = { onOpen(film.asItem()) },
                            modifier = Modifier
                                .padding(horizontal = ScreenPadding)
                                .riseIn(film.id, index, shownFilms),
                        )
                    }
                    if (state.loading) {
                        items(4, key = { "sk_$it" }) {
                            Box(
                                Modifier
                                    .padding(horizontal = ScreenPadding)
                                    .fillMaxWidth()
                                    .height(84.dp)
                                    .clip(CvShape.Large)
                                    .shimmer()
                            )
                        }
                    } else if (rows.isEmpty() && state.films.isNotEmpty()) {
                        item(key = "nomatch") {
                            Text(
                                "No films from the ${state.decade}s in the chart loaded so far.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.text3,
                                modifier = Modifier.padding(ScreenPadding),
                            )
                        }
                    } else if (state.failed && state.films.isEmpty()) {
                        item(key = "failed") {
                            Text(
                                "Box-office figures are unavailable right now. Pull back in a moment.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.text3,
                                modifier = Modifier.padding(ScreenPadding),
                            )
                        }
                    }
                }

                BoxView.Directors -> {
                    val directors = state.directors
                    if (directors == null) {
                        item(key = "building_d") { Building(state.leagueProgress, "Building the director league") }
                    } else {
                        item(key = "d_note") {
                            Text(
                                "Ranked by what their films in the all-time chart made. A hit is twice the budget (2.5× for an Indian production).",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.text3,
                                modifier = Modifier.padding(horizontal = ScreenPadding),
                            )
                        }
                        val top = directors.firstOrNull()?.revenue ?: 1L
                        items(directors.size, key = { "d_${directors[it].id}" }) { index ->
                            DirectorRow(
                                rank = index + 1,
                                director = directors[index],
                                share = directors[index].revenue.toFloat() / top.coerceAtLeast(1),
                                onClick = { onPerson(directors[index].id) },
                                modifier = Modifier
                                    .padding(horizontal = ScreenPadding)
                                    .riseIn(directors[index].id, index, shownDirectors),
                            )
                        }
                    }
                }

                BoxView.Franchises -> {
                    val league = state.league
                    if (league == null) {
                        item(key = "building") { Building(state.leagueProgress, "Building the franchise league") }
                    } else {
                        val top = league.firstOrNull()?.revenue ?: 1L
                        items(league.size, key = { "l_${league[it].id}" }) { index ->
                            LeagueRow(
                                rank = index + 1,
                                franchise = league[index],
                                share = league[index].revenue.toFloat() / top.coerceAtLeast(1),
                                onClick = { onCollection(league[index].id) },
                                modifier = Modifier
                                    .padding(horizontal = ScreenPadding)
                                    .riseIn(league[index].id, index, shownLeague),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxHero(state: BoxOfficeState) {
    val colors = CvTheme.colors
    // A slow gold glow that breathes behind the total — the website's orbiting
    // coins, reduced to the one thing they were doing: catching the light.
    val glow by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "glowPhase",
    )
    val reduced = CvTheme.reducedMotion
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding)
            .clip(CvShape.XLarge)
            .drawBehind {
                val phase = if (reduced) 0.5f else glow
                drawRect(
                    Brush.radialGradient(
                        listOf(Palette.Gold.copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(size.width * (0.65f + phase * 0.25f), size.height * (0.2f + phase * 0.3f)),
                        radius = size.width * 0.75f,
                    )
                )
            }
            .glass(CvShape.XLarge, strength = 0.7f)
            .padding(20.dp)
    ) {
        Column {
            Text("WORLDWIDE REVENUE", style = KickerStyle, color = colors.text3)
            Spacer(Modifier.height(6.dp))
            AnimatedContent(
                targetState = state.view,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "boxHero",
            ) { view ->
                Column {
                    val (total, line) = when (view) {
                        BoxView.Films -> state.films.sumOf { it.revenue } to
                            "across the ${state.films.size} highest-grossing films loaded"
                        BoxView.Franchises -> state.league?.let { league ->
                            league.sumOf { it.revenue } to "across the ${league.size} biggest franchises"
                        } ?: (0L to "Building the franchise league…")
                        BoxView.Directors -> state.directors?.let { directors ->
                            directors.sumOf { it.revenue } to "across the ${directors.size} directors ranked"
                        } ?: (0L to "Building the director league…")
                    }
                    if (total > 0) {
                        CountUpText(
                            total,
                            style = MaterialTheme.typography.displaySmall,
                            color = colors.gold,
                            format = { Money.usd(it) },
                        )
                    } else {
                        // Never "Not reported" for a figure that is merely still
                        // being worked out.
                        Text("…", style = MaterialTheme.typography.displaySmall, color = colors.gold)
                    }
                    Text(line, style = MaterialTheme.typography.labelMedium, color = colors.text2)
                }
            }
        }
    }
}

@Composable
private fun FilmRow(
    rank: Int,
    film: FilmMoney,
    share: Float,
    rate: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    Row(
        modifier
            .fillMaxWidth()
            .glass(CvShape.Large, strength = 0.6f)
            .clickableNoRipple { haptics?.play(Haptic.Tap); onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "%02d".format(rank),
            style = MaterialTheme.typography.titleLarge.tabular(),
            color = if (rank <= 3) colors.gold else colors.text3,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.Center,
        )
        Box(Modifier.width(44.dp).height(66.dp).clip(CvShape.Small)) {
            CvImage(Img.tiny(film.poster), film.title, Modifier.matchParentSize())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                film.title,
                style = MaterialTheme.typography.labelLarge,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildList {
                    if (film.year.isNotBlank()) add(film.year)
                    if (film.runtime > 0) add("${film.runtime} min")
                    if (film.budget > 0) add("${Money.multiple(film.multiple)} budget")
                }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = colors.text3,
                maxLines = 1,
            )
            Spacer(Modifier.height(7.dp))
            GrowBar(share, color = if (rank <= 3) colors.gold else Palette.Red2, height = 5.dp, delayMillis = 120)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                Money.usd(film.revenue),
                style = MaterialTheme.typography.titleSmall.tabular(),
                color = colors.text,
            )
            if (film.isIndian) {
                Text(
                    Money.inr(film.revenue, rate),
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = colors.gold,
                )
            }
        }
    }
}

@Composable
private fun LeagueRow(
    rank: Int,
    franchise: FranchiseMoney,
    share: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    Row(
        modifier
            .fillMaxWidth()
            .glass(CvShape.Large, strength = 0.6f)
            .clickableNoRipple { haptics?.play(Haptic.Tap); onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "%02d".format(rank),
            style = MaterialTheme.typography.titleLarge.tabular(),
            color = if (rank <= 3) colors.gold else colors.text3,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.Center,
        )
        Box(Modifier.width(44.dp).height(66.dp).clip(CvShape.Small)) {
            CvImage(
                Img.tiny(franchise.poster ?: franchise.topFilm?.poster),
                franchise.name,
                Modifier.matchParentSize(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                franchise.name.removeSuffix(" Collection"),
                style = MaterialTheme.typography.labelLarge,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${franchise.films.size} films" + (franchise.topFilm?.let { " · top: ${it.title}" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = colors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(7.dp))
            GrowBar(share, color = if (rank <= 3) colors.gold else Palette.Red2, height = 5.dp, delayMillis = 120)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            Money.usd(franchise.revenue),
            style = MaterialTheme.typography.titleSmall.tabular(),
            color = colors.text,
        )
    }
}

/** The league is built from the whole chart, which takes a moment; say how long. */
@Composable
private fun DirectorRow(
    rank: Int,
    director: com.cineverse.app.data.boxoffice.DirectorMoney,
    share: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    Row(
        modifier
            .fillMaxWidth()
            .glass(CvShape.Large, strength = 0.6f)
            .clickableNoRipple { haptics?.play(Haptic.Tap); onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "%02d".format(rank),
            style = MaterialTheme.typography.titleLarge.tabular(),
            color = if (rank <= 3) colors.gold else colors.text3,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.Center,
        )
        Box(Modifier.size(52.dp).clip(CvShape.Circle).background(colors.surface2), contentAlignment = Alignment.Center) {
            if (director.profile != null) {
                CvImage(Img.profile(director.profile), director.name, Modifier.matchParentSize())
            } else {
                Text(director.name.take(1), style = MaterialTheme.typography.titleMedium, color = colors.text3)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    director.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                director.consistency.score?.let { score ->
                    Spacer(Modifier.width(6.dp))
                    Text(
                        director.consistency.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (score >= 70) colors.green else colors.text3,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(CvShape.Pill)
                            .background((if (score >= 70) colors.green else colors.text).copy(alpha = 0.10f))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                buildList {
                    add("${director.films.size} film${if (director.films.size == 1) "" else "s"}")
                    director.hitRate?.let { add("$it% hits") }
                    director.topFilm?.let { add("top: ${it.title}") }
                }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = colors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(7.dp))
            GrowBar(share, color = if (rank <= 3) colors.gold else Palette.Red2, height = 5.dp, delayMillis = 120)
        }
        Spacer(Modifier.width(12.dp))
        Text(Money.usd(director.revenue), style = MaterialTheme.typography.titleSmall.tabular(), color = colors.text)
    }
}

@Composable
private fun Building(progress: Float, title: String = "Building the league") {
    val colors = CvTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.text)
        Spacer(Modifier.height(6.dp))
        Text(
            "Reading the chart · ${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium.tabular(),
            color = colors.text3,
        )
        Spacer(Modifier.height(14.dp))
        Box(Modifier.width(220.dp)) {
            // Not a GrowBar: this one tracks live progress, it does not arrive.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CvShape.Pill)
                    .background(colors.text.copy(alpha = 0.08f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                        .height(6.dp)
                        .clip(CvShape.Pill)
                        .background(colors.gold)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Ranked from the highest-grossing films TMDB reports.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.text3,
        )
    }
}

// ---------- state ----------

private val Decades = listOf(0, 2020, 2010, 2000, 1990, 1980, 1970)

enum class BoxView(val label: String) { Films("Films"), Franchises("Franchises"), Directors("Directors") }

enum class BoxSort(val label: String) {
    Gross("Worldwide gross"), Profit("Above budget"), Return("Best return"), Newest("Newest"),
}

data class BoxOfficeState(
    /** The chart, in revenue order. Rank is position in this list. */
    val films: List<FilmMoney> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 1,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val view: BoxView = BoxView.Films,
    val decade: Int = 0,
    val sort: BoxSort = BoxSort.Gross,
    val league: List<FranchiseMoney>? = null,
    val directors: List<com.cineverse.app.data.boxoffice.DirectorMoney>? = null,
    val leagueProgress: Float = 0f,
    val rate: Double = 90.0,
) {
    /** (rank, film), filtered and sorted. The rank is always the chart rank. */
    val visibleFilms: List<Pair<Int, FilmMoney>>
        get() {
            val ranked = films.mapIndexed { index, film -> index + 1 to film }
                .filter { (_, film) ->
                    decade == 0 || (film.year.toIntOrNull()?.let { it / 10 * 10 } == decade)
                }
            return when (sort) {
                BoxSort.Gross -> ranked
                BoxSort.Profit -> ranked.sortedByDescending { it.second.profit }
                BoxSort.Return -> ranked.sortedByDescending { it.second.multiple ?: -1.0 }
                BoxSort.Newest -> ranked.sortedByDescending { it.second.releaseDate }
            }
        }
}

class BoxOfficeViewModel(private val app: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(BoxOfficeState())
    val state: StateFlow<BoxOfficeState> = _state.asStateFlow()

    private var building = false

    init { loadMore() }

    fun loadMore() {
        val current = _state.value
        if (current.loading || (current.page > 0 && current.page >= current.totalPages)) return
        _state.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            val number = current.page + 1
            val (rows, total) = app.boxOffice.page(number)
            if (rows.any { it.isIndian }) {
                val rate = app.boxOffice.usdToInr()
                _state.update { it.copy(rate = rate) }
            }
            _state.update { held ->
                held.copy(
                    films = (held.films + rows).distinctBy { it.id }.sortedByDescending { it.revenue },
                    // A failed page does not advance, so the next scroll retries it.
                    page = if (rows.isEmpty()) held.page else number,
                    totalPages = total,
                    loading = false,
                    failed = rows.isEmpty(),
                )
            }
        }
    }

    fun setView(view: BoxView) {
        _state.update { it.copy(view = view) }
        if (view == BoxView.Franchises && _state.value.league == null) buildLeague(view)
        if (view == BoxView.Directors && _state.value.directors == null) buildLeague(view)
    }

    fun setDecade(decade: Int) = _state.update { it.copy(decade = decade) }

    fun setSort(sort: BoxSort) = _state.update { it.copy(sort = sort) }

    /**
     * The league needs the whole chart, so every page not yet loaded is fetched
     * together — four at a time — with the progress said out loud.
     */
    private fun buildLeague(view: BoxView) {
        if (building) return
        building = true
        viewModelScope.launch {
            try {
                val chart = fullChart()
                if (view == BoxView.Directors) {
                    val directors = app.boxOffice.directors(chart) { done ->
                        // The second half of the bar: credits, film by film.
                        _state.update { it.copy(leagueProgress = 0.5f + done * 0.5f) }
                    }
                    _state.update { it.copy(directors = directors, leagueProgress = 1f) }
                } else {
                    val league = app.boxOffice.league(chart)
                    _state.update { it.copy(league = league, leagueProgress = 1f) }
                }
            } finally {
                building = false
                // Asked for the other league while this one was building.
                val now = _state.value
                if (now.view == BoxView.Franchises && now.league == null) buildLeague(BoxView.Franchises)
                if (now.view == BoxView.Directors && now.directors == null) buildLeague(BoxView.Directors)
            }
        }
    }

    /** All ten pages of the chart, loading what is missing four at a time. */
    private suspend fun fullChart(): List<FilmMoney> {
        run {
            val held = _state.value
            // The chart is ten pages deep by definition (see BoxOfficeRepository.page).
            val remaining = ((held.page + 1)..10).toList()
            var done = 0
            val gate = Semaphore(4)
            val extra = coroutineScope {
                remaining.map { number ->
                    async {
                        gate.withPermit {
                            app.boxOffice.page(number).first.also {
                                done++
                                _state.update { s -> s.copy(leagueProgress = 0.5f * done / (remaining.size + 1)) }
                            }
                        }
                    }
                }.awaitAll().flatten()
            }
            val chart = (held.films + extra).distinctBy { it.id }.sortedByDescending { it.revenue }
            _state.update { it.copy(films = chart, page = it.totalPages, leagueProgress = 0.5f) }
            return chart
        }
    }
}
