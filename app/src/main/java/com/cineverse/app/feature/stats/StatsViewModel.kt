package com.cineverse.app.feature.stats

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cineverse.app.AppContainer
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.ShowProgress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

@Immutable
data class Figure(val label: String, val value: String, val detail: String = "")

@Immutable
data class DiaryDay(val dayStart: Long, val count: Int, val minutes: Int)

@Immutable
data class GenreSlice(val name: String, val count: Int, val share: Float)

@Immutable
data class StatsState(
    val signedIn: Boolean = false,
    val figures: List<Figure> = emptyList(),
    val diary: List<DiaryDay> = emptyList(),
    val genres: List<GenreSlice> = emptyList(),
    val months: List<Pair<String, Int>> = emptyList(),
    val streak: Int = 0,
    val longestStreak: Int = 0,
    val totalMinutes: Int = 0,
    val loaded: Boolean = false,
    val deep: DeepStats = DeepStats(),
)

/**
 * Every figure the stats page shows is DERIVED, never stored.
 *
 * That is the rule the website arrived at the hard way: a cached total is a
 * total that can be wrong, and a figure that disagrees with the list it came
 * from destroys trust in the whole page. Everything here is computed from the
 * watched documents and the episode log on each emission, which is cheap because
 * both are already in memory.
 */
class StatsViewModel(private val app: AppContainer) : ViewModel() {

    /** Which panels are folded, live from the same document the website uses. */
    val sections: StateFlow<Set<String>> = app.statsSections.collapsed

    fun toggleSection(id: String) = app.statsSections.toggle(id)

    val state: StateFlow<StatsState> =
        combine(
            app.library.library,
            app.episodes.progress,
            app.auth.user,
        ) { lib, shows, user ->
            if (user == null) StatsState(signedIn = false, loaded = true)
            else build(lib, shows)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())

    private fun build(lib: Library, shows: Map<Int, ShowProgress>): StatsState {
        val films = lib.watched.values.filter { it.type == com.cineverse.app.data.model.MediaType.Movie }
        val series = lib.watched.values.filter { it.type == com.cineverse.app.data.model.MediaType.Tv }

        // Films count their own runtime; a series counts the episodes actually
        // ticked, never its whole run — marking a show watched and watching it
        // are the same thing here only because the app ticks every episode when
        // you do.
        val filmMinutes = films.sumOf { if (it.runtime > 0) it.runtime else 110 }
        val episodeMinutes = shows.values.sumOf { it.minutesWatched }
        val totalMinutes = filmMinutes + episodeMinutes
        val episodes = shows.values.sumOf { it.watchedCount }

        val log = shows.values.flatMap { show ->
            show.log.map { it.stamp to (show.episodeRuntime.takeIf { r -> r > 0 } ?: 42) }
        } + films.mapNotNull { film ->
            film.watchedAt.takeIf { it > 0 }?.let { it to (film.runtime.takeIf { r -> r > 0 } ?: 110) }
        }

        val byDay = log.groupBy { startOfDay(it.first) }
        val diary = (0 until 84).map { back ->
            val day = startOfDay(System.currentTimeMillis()) - back * DAY
            val entries = byDay[day].orEmpty()
            DiaryDay(day, entries.size, entries.sumOf { it.second })
        }.reversed()

        val streak = currentStreak(byDay.keys)
        val longest = longestStreak(byDay.keys)

        val genreNames = com.cineverse.app.data.model.GenreNames
        val genreCounts = lib.watched.values
            .flatMap { it.genres }
            .mapNotNull { genreNames[it] }
            .groupingBy { it }
            .eachCount()
            .entries.sortedByDescending { it.value }
            .take(8)
        val genreTotal = genreCounts.sumOf { it.value }.coerceAtLeast(1)
        val genres = genreCounts.map { GenreSlice(it.key, it.value, it.value.toFloat() / genreTotal) }

        val byMonth = log.groupBy { monthKey(it.first) }
        val months = (11 downTo 0).map { back ->
            val calendar = Calendar.getInstance().apply { add(Calendar.MONTH, -back) }
            val key = monthKey(calendar.timeInMillis)
            val label = java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault())
                .format(calendar.time)
            label to byMonth[key].orEmpty().size
        }

        val figures = listOf(
            Figure("Watch time", hours(totalMinutes), "${totalMinutes / 60} hours in total"),
            Figure("Films", films.size.toString(), "${filmMinutes / 60}h"),
            Figure("Series", series.size.toString(), "$episodes episodes"),
            Figure("Current streak", "$streak", if (streak == 1) "day" else "days"),
            Figure("Longest streak", "$longest", if (longest == 1) "day" else "days"),
            Figure("In your list", lib.saved.size.toString(), "waiting"),
        )

        return StatsState(
            signedIn = true,
            figures = figures,
            diary = diary,
            genres = genres,
            months = months,
            streak = streak,
            longestStreak = longest,
            totalMinutes = totalMinutes,
            deep = buildDeepStats(lib, shows, totalMinutes, streak, longest),
            loaded = true,
        )
    }

    private fun hours(minutes: Int): String {
        val h = minutes / 60
        return when {
            h >= 1000 -> "${h / 1000}.${(h % 1000) / 100}k h"
            else -> "${h}h"
        }
    }

    /** Days in a row ending today or yesterday — missing today is not a break yet. */
    private fun currentStreak(days: Set<Long>): Int {
        if (days.isEmpty()) return 0
        val today = startOfDay(System.currentTimeMillis())
        var cursor = if (days.contains(today)) today else today - DAY
        if (!days.contains(cursor)) return 0
        var count = 0
        while (days.contains(cursor)) { count++; cursor -= DAY }
        return count
    }

    private fun longestStreak(days: Set<Long>): Int {
        if (days.isEmpty()) return 0
        val sorted = days.sorted()
        var best = 1
        var run = 1
        for (index in 1 until sorted.size) {
            run = if (sorted[index] - sorted[index - 1] == DAY) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    private companion object {
        const val DAY = 86_400_000L

        fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        fun monthKey(millis: Long): String = Calendar.getInstance().apply {
            timeInMillis = millis
        }.let { "${it.get(Calendar.YEAR)}-${it.get(Calendar.MONTH)}" }
    }
}
