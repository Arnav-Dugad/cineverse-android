package com.cineverse.app.feature.diary

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import com.cineverse.app.core.ui.BottomBarSpace
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.Img
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.core.ui.collapse
import com.cineverse.app.data.diary.Diary
import com.cineverse.app.data.diary.DiaryDay
import com.cineverse.app.data.diary.DiaryEntry
import com.cineverse.app.data.diary.Streak
import com.cineverse.app.data.model.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Immutable
data class DiaryState(
    val month: YearMonth = YearMonth.now(),
    val selected: LocalDate = LocalDate.now(),
    val days: Map<LocalDate, DiaryDay> = emptyMap(),
    val streak: Streak = Streak(0, 0, false, null),
    val onThisDay: List<DiaryEntry> = emptyList(),
    val loaded: Boolean = false,
    /** Gemini's line for the month shown, once written. */
    val blurb: String? = null,
) {
    val monthDays: List<DiaryDay> get() = days.values.filter { YearMonth.from(it.date) == month }
    val monthMinutes: Int get() = monthDays.sumOf { it.minutes }
    val lastMonthMinutes: Int get() = days.values.filter { YearMonth.from(it.date) == month.minusMonths(1) }.sumOf { it.minutes }
    val monthCount: Int get() = monthDays.sumOf { it.entries.size }
    val busiest: Int get() = monthDays.maxOfOrNull { it.minutes } ?: 0
}

class DiaryViewModel(private val app: AppContainer) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val selected = MutableStateFlow(LocalDate.now())
    private val blurbs = MutableStateFlow<Map<String, String>>(emptyMap())

    val state: StateFlow<DiaryState> = combine(
        combine(app.library.library, app.episodes.progress, ::Pair),
        month, selected, blurbs, app.settings.settings,
    ) { (lib, shows), m, s, written, settings ->
        val entries = Diary.entries(lib, shows)
        val days = Diary.days(entries)
        val inMonth = days.values.filter { YearMonth.from(it.date) == m }.flatMap { it.entries }
            .filterNot { app.privacy.hidden(it.item.key, lib) }
        DiaryState(
            month = m,
            selected = s,
            days = days,
            streak = Diary.streak(days.keys),
            onThisDay = Diary.onThisDay(entries),
            loaded = lib.loaded,
            blurb = if (settings.geminiOn) written["$m:${inMonth.size}"] ?: app.monthBlurb.cached(m, inMonth) else null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiaryState())

    init {
        // The month's line, written once its viewings are known, and again if
        // the month gains one. A month turned past quickly is not asked about.
        viewModelScope.launch {
            state
                .filter { it.loaded && it.blurb == null && app.settings.settings.value.geminiOn }
                .map { it.month to it.monthDays.flatMap { day -> day.entries }.filterNot { e -> app.privacy.hidden(e.item.key) } }
                .distinctUntilChanged { a, b -> a.first == b.first && a.second.size == b.second.size }
                .collectLatest { (m, entries) ->
                    kotlinx.coroutines.delay(400)
                    val line = runCatching { app.monthBlurb.blurb(m, entries) }.getOrNull() ?: return@collectLatest
                    blurbs.value = blurbs.value + ("$m:${entries.size}" to line)
                }
        }
    }

    fun move(by: Long) {
        val next = month.value.plusMonths(by)
        if (next.isAfter(YearMonth.now())) return
        month.value = next
        // The selection follows into the new month: its last active day, or its first.
        val active = state.value.days.keys.filter { YearMonth.from(it) == next }.maxOrNull()
        selected.value = active ?: next.atDay(1)
    }

    fun select(day: LocalDate) { selected.value = day }
}

/**
 * The Watch Diary: a month of what you watched, each day shaded by how long
 * you spent and carrying the poster of what you watched; the day you pick, in
 * full, in full; your
 * streak; and what you watched on this date in years gone by.
 */
@Composable
fun DiaryScreen(viewModel: DiaryViewModel, onOpen: (MediaItem) -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val selectedDay = state.days[state.selected]
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    Column(Modifier.fillMaxSize().background(colors.ink)) {
    com.cineverse.app.core.ui.CvScreenBar(title = "Diary", onBack = onBack, titleAlpha = { list.collapse(80f * density) })
    LazyColumn(
        Modifier.fillMaxSize(),
        state = list,
        contentPadding = PaddingValues(bottom = BottomBarSpace),
    ) {
        item(key = "large") { com.cineverse.app.core.ui.LargeTitle("Diary", list) }
        item(key = "month") {
            MonthHeader(state, onMove = { haptics?.play(Haptic.Detent); viewModel.move(it) })
        }
        item(key = "calendar") {
            var drag by remember { mutableFloatStateOf(0f) }
            AnimatedContent(
                state.month,
                transitionSpec = {
                    val forward = targetState.isAfter(initialState)
                    (slideInHorizontally(tween(300)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(220)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(200)))
                },
                label = "diaryMonth",
                modifier = Modifier.pointerInput(Unit) {
                    // A swipe across the calendar turns the month, as on the website.
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = {
                            if (drag < -120f) viewModel.move(1) else if (drag > 120f) viewModel.move(-1)
                        },
                    ) { _, amount -> drag += amount }
                },
            ) { month ->
                Calendar(month, state, onPick = { haptics?.play(Haptic.Select); viewModel.select(it) })
            }
        }
        item(key = "streak") { StreakRow(state.streak) }
        item(key = "dayHead") {
            Text(
                state.selected.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale())),
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
                modifier = Modifier.padding(horizontal = ScreenPadding).padding(top = 22.dp, bottom = 8.dp),
            )
        }
        val entries = selectedDay?.entries.orEmpty()
        if (entries.isEmpty()) {
            item(key = "empty") {
                Text(
                    if (state.selected == LocalDate.now()) "Nothing yet today." else "Nothing watched on this day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text3,
                    modifier = Modifier.padding(horizontal = ScreenPadding),
                )
            }
        } else {
            items(entries.reversed(), key = { "e_${it.item.key}_${it.at}_${it.episode}" }) { entry ->
                EntryRow(entry, onOpen)
            }
        }
        if (state.onThisDay.isNotEmpty()) {
            item(key = "otdHead") {
                Text(
                    "ON THIS DAY",
                    style = KickerStyle,
                    color = colors.text3,
                    modifier = Modifier.padding(horizontal = ScreenPadding).padding(top = 26.dp, bottom = 8.dp),
                )
            }
            items(state.onThisDay.take(8), key = { "otd_${it.item.key}_${it.at}_${it.episode}" }) { entry ->
                EntryRow(entry, onOpen, showYear = true)
            }
        }
    }
    }
}

@Composable
private fun MonthHeader(state: DiaryState, onMove: (Long) -> Unit) {
    val colors = CvTheme.colors
    val canGoOn = state.month.isBefore(YearMonth.now())
    Column(Modifier.padding(horizontal = ScreenPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.month.month.getDisplayName(TextStyle.FULL, locale()) + " " + state.month.year,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
            Arrow(Icons.Rounded.ChevronLeft, "Previous month", true) { onMove(-1) }
            Spacer(Modifier.width(6.dp))
            Arrow(Icons.Rounded.ChevronRight, "Next month", canGoOn) { onMove(1) }
        }
        val hours = state.monthMinutes / 60
        val last = state.lastMonthMinutes / 60
        Text(
            buildString {
                append("${state.monthCount} ${if (state.monthCount == 1) "viewing" else "viewings"} · ${hours}h")
                if (last > 0 || hours > 0) {
                    val diff = hours - last
                    append(" · ")
                    append(if (diff >= 0) "+${diff}h" else "${diff}h")
                    append(" on last month")
                }
            },
            style = MaterialTheme.typography.labelMedium,
            color = colors.text3,
        )
        // Gemini's line for the month, arriving with a soft rise when written.
        AnimatedContent(
            state.blurb,
            transitionSpec = { (fadeIn(tween(420)) + androidx.compose.animation.slideInVertically(tween(420)) { it / 2 }) togetherWith fadeOut(tween(160)) },
            label = "monthBlurb",
        ) { blurb ->
            if (blurb != null) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        null,
                        tint = com.cineverse.app.core.ui.GeminiColors[1],
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        blurb,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            brush = com.cineverse.app.core.ui.geminiBrushStatic,
                        ),
                    )
                }
            } else {
                Spacer(Modifier.height(0.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Arrow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = CvTheme.colors
    Box(
        Modifier
            .size(38.dp)
            .clip(CvShape.Circle)
            .background(colors.glass)
            .clickableNoRipple { if (enabled) onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (enabled) colors.text else colors.text3.copy(alpha = 0.4f))
    }
}

@Composable
private fun Calendar(month: YearMonth, state: DiaryState, onPick: (LocalDate) -> Unit) {
    val colors = CvTheme.colors
    val first = month.atDay(1)
    val lead = (first.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val cells = lead + month.lengthOfMonth()
    val rows = (cells + 6) / 7
    val today = LocalDate.now()
    val busiest = state.days.values.filter { YearMonth.from(it.date) == month }.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
    Column(Modifier.padding(horizontal = ScreenPadding - 4.dp)) {
        Row {
            for (day in DayOfWeek.entries) {
                Text(
                    day.getDisplayName(TextStyle.NARROW, locale()),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.text3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(bottom = 6.dp),
                )
            }
        }
        for (row in 0 until rows) {
            Row {
                for (col in 0 until 7) {
                    val index = row * 7 + col - lead
                    if (index < 0 || index >= month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f).aspectRatio(0.72f))
                        continue
                    }
                    val date = month.atDay(index + 1)
                    val day = state.days[date]
                    DayCell(
                        date = date,
                        day = day,
                        // Shaded in steps by minutes, one hue, darker is more.
                        shade = day?.let { (it.minutes.toFloat() / busiest).coerceIn(0.12f, 1f) } ?: 0f,
                        today = date == today,
                        selected = date == state.selected,
                        future = date.isAfter(today),
                        modifier = Modifier.weight(1f),
                        onClick = { onPick(date) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    day: DiaryDay?,
    shade: Float,
    today: Boolean,
    selected: Boolean,
    future: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = CvTheme.colors
    val poster = day?.entries?.lastOrNull()?.item?.posterPath
    Box(
        modifier
            .padding(2.dp)
            .aspectRatio(0.72f)
            .clip(CvShape.Small)
            .background(if (day != null) Palette.Red2.copy(alpha = 0.10f + 0.45f * shade) else colors.text.copy(alpha = 0.03f))
            .border(
                width = if (selected) 2.dp else if (today) 1.dp else 0.dp,
                color = if (selected) colors.text else if (today) Palette.Red2 else Color.Transparent,
                shape = CvShape.Small,
            )
            .clickableNoRipple { if (!future) onClick() },
    ) {
        if (poster != null) {
            CvImage(Img.poster(poster), null, Modifier.fillMaxSize().padding(3.dp).clip(CvShape.Tiny))
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }
        Text(
            "${date.dayOfMonth}",
            style = MaterialTheme.typography.labelSmall,
            color = when {
                poster != null -> Color.White
                future -> colors.text3.copy(alpha = 0.4f)
                else -> colors.text2
            },
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
        )
        val count = day?.entries?.size ?: 0
        if (count > 1) {
            Text(
                "$count",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .clip(CvShape.Pill)
                    .background(Palette.Red2)
                    .padding(horizontal = 5.dp),
            )
        }
    }
}

@Composable
private fun StreakRow(streak: Streak) {
    val colors = CvTheme.colors
    Row(
        Modifier.padding(horizontal = ScreenPadding).padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(CvShape.Large)
                .background(colors.text.copy(alpha = 0.04f))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.cineverse.app.core.ui.StreakFlame(lit = streak.current > 0, size = 30.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("${streak.current}-day streak", style = MaterialTheme.typography.titleSmall, color = colors.text)
                Text(
                    if (streak.current > 0 && !streak.todayActive) "Watch something today to keep it" else "Days in a row",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.text3,
                )
            }
        }
        Column(
            Modifier
                .clip(CvShape.Large)
                .background(colors.text.copy(alpha = 0.04f))
                .padding(12.dp),
        ) {
            Text("${streak.longest} days", style = MaterialTheme.typography.titleSmall, color = colors.text)
            Text("Longest", style = MaterialTheme.typography.labelSmall, color = colors.text3)
        }
    }
}

@Composable
private fun EntryRow(entry: DiaryEntry, onOpen: (MediaItem) -> Unit, showYear: Boolean = false) {
    val colors = CvTheme.colors
    val time = Instant.ofEpochMilli(entry.at).atZone(ZoneId.systemDefault())
    Row(
        Modifier
            .fillMaxWidth()
            .clickableNoRipple { onOpen(entry.item) }
            .padding(horizontal = ScreenPadding, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 46.dp, height = 69.dp).clip(CvShape.Small).background(colors.surface2)) {
            CvImage(Img.poster(entry.item.posterPath), entry.item.title, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.item.title, style = MaterialTheme.typography.titleSmall, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    entry.episode.ifBlank { "Film" },
                    entry.minutes.takeIf { it > 0 }?.let { if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m" },
                    if (showYear) time.year.toString() else time.format(DateTimeFormatter.ofPattern("HH:mm")),
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = colors.text3,
            )
        }
    }
}



/** The locale, read so a change of language redraws the diary. */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
private fun locale(): Locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
