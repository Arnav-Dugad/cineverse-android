package com.cineverse.app.data.diary

import androidx.compose.runtime.Immutable
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.model.ShowProgress
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One viewing: a film played, or an episode ticked as you watched it. */
@Immutable
data class DiaryEntry(
    val at: Long,
    val item: MediaItem,
    val minutes: Int,
    /** "S2 E4" for an episode, empty for a film. */
    val episode: String = "",
    /** Always 1: a title appears once in the Diary. */
    val viewing: Int = 1,
    /** The date it appears on. */
    val allViewings: List<Long> = emptyList(),
) {
    val day: LocalDate get() = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()
}

/** A day of the diary. */
@Immutable
data class DiaryDay(val date: LocalDate, val entries: List<DiaryEntry>) {
    val minutes: Int get() = entries.sumOf { it.minutes }
}

/** A run of consecutive days with something watched. */
@Immutable
data class Streak(val current: Int, val longest: Int, val todayActive: Boolean, val start: LocalDate?)

/**
 * The Watch Diary, the website's: every viewing on the day it happened, made
 * from what the library already holds.
 *
 * What counts as viewing is the website's rule. A film counts once, on the
 * day it was last watched. An episode counts when it was ticked
 * on its own; a whole season or a back-filled history ticked in one go is
 * bookkeeping, not an evening, and never lands on a day. A series marked
 * watched as a whole, with no episodes ticked, is bookkeeping too.
 */
object Diary {

    fun entries(library: Library, shows: Map<Int, ShowProgress>, zone: ZoneId = ZoneId.systemDefault()): List<DiaryEntry> {
        val out = mutableListOf<DiaryEntry>()
        for (film in library.watched.values) {
            if (film.type != MediaType.Movie) continue
            // One day per film: when it was last watched.
            val at = film.lastPlay.takeIf { it > 0 } ?: continue
            out += DiaryEntry(at, film.asItem(), film.runtime.coerceAtLeast(0), viewing = 1, allViewings = listOf(at))
        }
        for (show in shows.values) {
            val item = MediaItem(show.tmdbId, MediaType.Tv, show.title, posterPath = show.poster.ifBlank { null }, backdropPath = show.backdrop.ifBlank { null })
            val seen = HashSet<Long>()
            for (row in show.log.filterNot { it.bulk }.sortedBy { it.stamp }) {
                if (row.stamp <= 0) continue
                // Each episode once, on the day it was first ticked.
                if (!seen.add(row.episodeKey)) continue
                out += DiaryEntry(
                    at = row.stamp,
                    item = item,
                    minutes = show.episodeRuntime.coerceAtLeast(0),
                    episode = if (show.isAbsolute) "Episode ${row.episode}" else "S${row.season} E${row.episode}",
                    viewing = 1,
                    allViewings = listOf(row.stamp),
                )
            }
        }
        return out.sortedBy { it.at }
    }

    fun days(entries: List<DiaryEntry>): Map<LocalDate, DiaryDay> =
        entries.groupBy { it.day }.mapValues { (date, list) -> DiaryDay(date, list.sortedBy { it.at }) }

    fun streak(days: Set<LocalDate>, today: LocalDate = LocalDate.now()): Streak {
        val todayActive = today in days
        var cursor = if (todayActive) today else today.minusDays(1)
        var current = 0
        while (cursor in days) { current++; cursor = cursor.minusDays(1) }
        var longest = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in days.sorted()) {
            run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
            longest = maxOf(longest, run)
            previous = day
        }
        val start = if (current > 0) (if (todayActive) today else today.minusDays(1)).minusDays(current - 1L) else null
        return Streak(current, longest, todayActive, start)
    }

    /** What you watched on today's date in earlier years, newest year first. */
    fun onThisDay(entries: List<DiaryEntry>, today: LocalDate = LocalDate.now()): List<DiaryEntry> =
        entries.filter { val d = it.day; d.year < today.year && d.monthValue == today.monthValue && d.dayOfMonth == today.dayOfMonth }
            .sortedByDescending { it.at }

    /** The streak milestones the website celebrates. */
    val MILESTONES = listOf(7, 30, 100)

    /** The milestone to celebrate today, or null: exactly that long, today included, not yet announced. */
    fun milestoneToday(streak: Streak, announced: Set<String>): Int? {
        if (!streak.todayActive || streak.start == null) return null
        val days = MILESTONES.firstOrNull { it == streak.current } ?: return null
        return days.takeIf { "${streak.start}:$it" !in announced }
    }
}
