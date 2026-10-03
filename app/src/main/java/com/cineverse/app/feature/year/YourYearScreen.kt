package com.cineverse.app.feature.year

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import com.cineverse.app.core.design.Motion
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
import com.cineverse.app.core.ui.rememberArrival
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.feature.list.EmptyState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * The website's /year: every film you watched and every series you finished in
 * one year, month by month, with the year before for scale.
 *
 * The month chart is the centre of it. Each bar stacks that month's film
 * viewings on the series finished in it, rises in turn when the page opens, and
 * opens to list exactly what made it up when tapped. Changing year slides the
 * whole page sideways, in the direction of time.
 */
@Composable
fun YourYearScreen(
    viewModel: YourYearViewModel,
    onOpen: (MediaItem) -> Unit,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = CvTheme.colors

    Column(modifier.fillMaxSize().background(colors.ink)) {
        CvScreenBar("Your year", onBack)

        if (!state.signedIn) {
            EmptyState(
                title = "Sign in to see your year",
                body = "Every film you watch and every series you finish gathers here, month by month.",
                action = "Sign in" to onSignIn,
            )
            return@Column
        }

        val summary = state.summary ?: return@Column
        LazyColumn(
            contentPadding = PaddingValues(bottom = BottomBarSpace),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "hero") { YearHero(state, onPick = viewModel::pickYear) }

            item(key = "page") {
                AnimatedContent(
                    targetState = summary,
                    contentKey = { it.year },
                    transitionSpec = {
                        // Older years are to the left, the way a timeline reads.
                        val forward = targetState.year > initialState.year
                        (slideInHorizontally(Motion.offset()) { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally(Motion.offset()) { if (forward) -it / 3 else it / 3 } + fadeOut())
                    },
                    label = "year",
                ) { year ->
                    // Keyed by the year, so every count and bar arrives again for
                    // the year just chosen rather than sliding from the last one.
                    key(year.year) {
                        YearPage(
                            summary = year,
                            previous = state.previous,
                            month = state.month,
                            onMonth = viewModel::toggleMonth,
                            onOpen = onOpen,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YearHero(state: YearState, onPick: (Int) -> Unit) {
    val colors = CvTheme.colors
    val summary = state.summary ?: return
    Column {
        Column(Modifier.padding(horizontal = ScreenPadding)) {
            Text("YOUR YEAR", style = KickerStyle, color = colors.text3)
            Spacer(Modifier.height(6.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Palette.Red2, fontStyle = FontStyle.Italic)) {
                        append(summary.year.toString())
                    }
                    append(" on CineVerse")
                },
                style = MaterialTheme.typography.headlineLarge,
                color = colors.text,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (summary.titles > 0) {
                    "${plural(summary.filmCount, "film")} and ${plural(summary.seriesCount, "finished series", "finished series")}, month by month."
                } else {
                    "Nothing marked for this year yet. Films you watch and series you finish will gather here."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text2,
            )
        }
        if (state.years.size > 1) {
            Spacer(Modifier.height(14.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.years, key = { it }) { year ->
                    CvChip(year.toString(), year == summary.year, { onPick(year) })
                }
            }
        }
    }
}

@Composable
private fun YearPage(
    summary: YearSummary,
    previous: YearSummary?,
    month: Int,
    onMonth: (Int) -> Unit,
    onOpen: (MediaItem) -> Unit,
) {
    val colors = CvTheme.colors
    val deltas = previous?.let { YearModel.deltas(summary, it) }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // ---- the total ----
        Column(
            Modifier
                .padding(horizontal = ScreenPadding)
                .fillMaxWidth()
                .glass(CvShape.XLarge, strength = 0.7f)
                .padding(18.dp)
        ) {
            Text("COMBINED TOTAL", style = KickerStyle, color = colors.text3)
            Row(verticalAlignment = Alignment.Bottom) {
                CountUpText(summary.titles.toLong(), style = MaterialTheme.typography.displayMedium, color = colors.text)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${if (summary.titles == 1) "title" else "titles"} in ${summary.year}",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text2,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Figure(
                    "Films", summary.filmCount.toLong(),
                    if (summary.viewings > summary.filmCount) "${summary.viewings} viewings" else "watched",
                    deltas?.first, Modifier.weight(1f),
                )
                Figure(
                    "Series", summary.seriesCount.toLong(),
                    if (summary.episodes > 0) "${summary.episodes} episodes" else "finished",
                    deltas?.second, Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Figure(
                    "Watch time", (summary.minutes / 60).toLong(), "hours, films + finished runs",
                    deltas?.third, Modifier.weight(1f),
                )
                Column(
                    Modifier
                        .weight(1f)
                        .clip(CvShape.Large)
                        .background(colors.text.copy(alpha = 0.04f))
                        .padding(12.dp)
                ) {
                    Text("Busiest", style = MaterialTheme.typography.labelSmall, color = colors.text3)
                    Text(
                        summary.busiest.joinToString(" · ") { shortMonth(it) }.ifBlank { "—" },
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.gold,
                        maxLines = 1,
                    )
                    Text(
                        summary.busiest.firstOrNull()?.let { "${summary.byMonth[it]} that month" } ?: "nothing yet",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.text3,
                    )
                }
            }
        }

        // ---- the months ----
        MonthChart(summary, month, onMonth, Modifier.padding(horizontal = ScreenPadding))

        AnimatedVisibility(
            visible = month in 0..11,
            enter = expandVertically(Motion.size()) + fadeIn(),
            exit = shrinkVertically(Motion.size()) + fadeOut(),
        ) {
            MonthDetail(summary, month.coerceIn(0, 11), onOpen)
        }

        // ---- moments ----
        val moments = YearModel.highlights(summary)
        if (moments.isNotEmpty()) {
            Column {
                Text(
                    "${summary.year} IN MOMENTS",
                    style = KickerStyle,
                    color = colors.text3,
                    modifier = Modifier.padding(horizontal = ScreenPadding),
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(moments.size) { index ->
                        val moment = moments[index]
                        MomentCard(moment, index) {
                            onOpen(MediaItem(moment.id, moment.type, moment.title, moment.poster.ifBlank { null }))
                        }
                    }
                }
            }
        }

        // ---- genres ----
        val genres = YearModel.topGenres(summary)
        if (genres.isNotEmpty()) {
            Column(
                Modifier
                    .padding(horizontal = ScreenPadding)
                    .fillMaxWidth()
                    .glass(CvShape.XLarge, strength = 0.6f)
                    .padding(18.dp)
            ) {
                Text("YOUR GENRES", style = KickerStyle, color = colors.text3)
                Spacer(Modifier.height(12.dp))
                genres.forEachIndexed { index, (name, count, share) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            name,
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.text,
                            modifier = Modifier.width(110.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        GrowBar(
                            share,
                            Modifier.weight(1f),
                            color = if (index == 0) colors.gold else Palette.Cyan2,
                            delayMillis = 200 + index * 90,
                        )
                        Text(
                            count.toString(),
                            style = MaterialTheme.typography.labelLarge.tabular(),
                            color = colors.text2,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(34.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: Long, note: String, delta: Delta?, modifier: Modifier = Modifier) {
    val colors = CvTheme.colors
    Column(
        modifier
            .clip(CvShape.Large)
            .background(colors.text.copy(alpha = 0.04f))
            .padding(12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.text3)
        CountUpText(value, style = MaterialTheme.typography.titleLarge, color = colors.text)
        Text(note, style = MaterialTheme.typography.labelSmall, color = colors.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (delta != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                delta.label,
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    delta.up -> colors.green
                    delta.down -> Palette.Red2
                    else -> colors.text3
                },
                maxLines = 1,
            )
        }
    }
}

/**
 * Twelve stacked bars: series finished (cyan) under film viewings (red), the
 * busiest month in gold. They rise left to right, 45 ms apart, the first time
 * the year is shown.
 */
@Composable
private fun MonthChart(summary: YearSummary, focus: Int, onMonth: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val top = (summary.byMonth.maxOrNull() ?: 0).coerceAtLeast(1)
    val busiest = summary.busiest.toSet()
    Column(modifier.fillMaxWidth().glass(CvShape.XLarge, strength = 0.6f).padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legend(Palette.Red2, "Film viewings")
            Legend(Palette.Cyan2, "Series finished")
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().height(170.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (month in 0 until 12) {
                val count = summary.byMonth[month]
                val films = summary.filmMonths[month]
                val series = summary.seriesMonths[month]
                val grown = rememberArrival(count.toFloat() / top, month * 45, 800)
                val selected = focus == month
                val lift by animateFloatAsState(if (selected) 1f else 0f, Motion.lively(), label = "lift")
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CvShape.Small)
                        .background(if (selected) colors.text.copy(alpha = 0.07f) else Color.Transparent)
                        .clickableNoRipple {
                            if (count == 0) return@clickableNoRipple
                            haptics?.play(Haptic.Select)
                            onMonth(month)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    if (count > 0) {
                        Text(
                            count.toString(),
                            style = MaterialTheme.typography.labelSmall.tabular(),
                            color = if (month in busiest) colors.gold else colors.text2,
                        )
                    }
                    Column(
                        Modifier
                            .fillMaxWidth(0.72f)
                            .weight(1f, fill = false)
                            .fillMaxHeight(grown.coerceAtLeast(if (count > 0) 0.03f else 0f))
                            .graphicsLayer { scaleX = 1f + lift * 0.12f }
                            .clip(CvShape.Tiny)
                            .then(
                                if (month in busiest) Modifier.border(1.5.dp, colors.gold, CvShape.Tiny)
                                else Modifier
                            ),
                    ) {
                        if (films > 0) {
                            Box(Modifier.fillMaxWidth().weight(films.toFloat()).background(Palette.Red2))
                        }
                        if (series > 0) {
                            Box(Modifier.fillMaxWidth().weight(series.toFloat()).background(Palette.Cyan2))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        shortMonth(month).take(1),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) colors.text else colors.text3,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap a month to see what made it up.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.text3,
        )
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    val colors = CvTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CvShape.Circle).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.text2)
    }
}

@Composable
private fun MonthDetail(summary: YearSummary, month: Int, onOpen: (MediaItem) -> Unit) {
    val colors = CvTheme.colors
    val films = summary.films.filter { film -> film.dates.any { YearModel.monthOf(it) == month } }
    val shows = summary.shows.filter { YearModel.monthOf(it.finishedAt) == month }
    val items = films.map { MediaItem(it.id, MediaType.Movie, it.title, it.poster.ifBlank { null }) } +
        shows.map { MediaItem(it.id, MediaType.Tv, it.title, it.poster.ifBlank { null }) }
    Column {
        Text(
            "${fullMonth(month).uppercase()} · " +
                listOfNotNull(
                    films.takeIf { it.isNotEmpty() }?.let { plural(it.size, "film") },
                    shows.takeIf { it.isNotEmpty() }?.let { plural(it.size, "series finished", "series finished") },
                ).joinToString(" · "),
            style = KickerStyle,
            color = colors.text3,
            modifier = Modifier.padding(horizontal = ScreenPadding),
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.key }) { item ->
                Column(Modifier.width(96.dp).clickableNoRipple { onOpen(item) }) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(144.dp)
                            .clip(CvShape.Medium)
                    ) {
                        CvImage(Img.poster(item.posterPath), item.title, Modifier.matchParentSize())
                        if (item.type == MediaType.Tv) {
                            Text(
                                "FINISHED",
                                style = KickerStyle,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp)
                                    .clip(CvShape.Tiny)
                                    .background(Palette.Cyan.copy(alpha = 0.85f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        item.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.text2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MomentCard(moment: Moment, index: Int, onClick: () -> Unit) {
    val colors = CvTheme.colors
    val arrive = rememberArrival(1f, 150 + index * 80, 650)
    Row(
        Modifier
            .width(250.dp)
            .graphicsLayer {
                alpha = arrive
                translationX = (1f - arrive) * 60f
            }
            .glass(CvShape.Large, strength = 0.8f)
            .clickableNoRipple(onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(52.dp).height(78.dp).clip(CvShape.Small)) {
            CvImage(Img.poster(moment.poster.ifBlank { null }), moment.title, Modifier.matchParentSize())
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Palette.Ink.copy(alpha = 0.4f))))
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(moment.label.uppercase(), style = KickerStyle, color = colors.gold)
            Text(
                moment.title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(moment.note, style = MaterialTheme.typography.labelSmall, color = colors.text3)
        }
    }
}

private fun plural(count: Int, one: String, many: String = "${one}s") = "$count ${if (count == 1) one else many}"

private fun fullMonth(month: Int): String =
    Month.of(month + 1).getDisplayName(TextStyle.FULL, Locale.getDefault())

private fun shortMonth(month: Int): String =
    Month.of(month + 1).getDisplayName(TextStyle.SHORT, Locale.getDefault())

// ---------- state ----------

data class YearState(
    val signedIn: Boolean = true,
    val years: List<Int> = emptyList(),
    val summary: YearSummary? = null,
    val previous: YearSummary? = null,
    /** 0–11 while a month is open, -1 otherwise. */
    val month: Int = -1,
)

class YourYearViewModel(app: AppContainer, requested: Int = 0) : ViewModel() {

    private val chosen = MutableStateFlow(requested)
    private val month = MutableStateFlow(-1)

    val state: StateFlow<YearState> = combine(
        app.library.library, app.episodes.progress, app.auth.uid, chosen, month,
    ) { library, shows, uid, chosen, month ->
        val years = YearModel.years(library, shows)
        val current = LocalDate.now().year
        // The year asked for; otherwise this one if anything happened in it,
        // otherwise the most recent year that has something.
        val year = when {
            chosen > 0 -> chosen
            current in years || years.isEmpty() -> current
            else -> years.first()
        }
        YearState(
            signedIn = uid != null,
            years = (years + year).distinct().sortedDescending(),
            summary = YearModel.summary(library, shows, year),
            previous = YearModel.summary(library, shows, year - 1),
            month = month,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YearState())

    fun pickYear(year: Int) {
        chosen.value = year
        month.value = -1
    }

    fun toggleMonth(value: Int) = month.update { if (it == value) -1 else value }
}
