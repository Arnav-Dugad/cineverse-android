package com.cineverse.app.data.inbox

import android.content.Context
import androidx.compose.runtime.Immutable
import com.cineverse.app.data.airing.AiringRepository
import com.cineverse.app.data.airing.Airing
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.model.ShowProgress
import com.cineverse.app.feature.year.YearModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class InboxKind(val label: String) {
    Episode("Episodes"), Season("Episodes"), Release("Releases"), Finished("Recaps"), Recap("Recaps")
}

/** One thing worth telling you, and when it became true. */
@Immutable
data class InboxEvent(
    val id: String,
    val kind: InboxKind,
    val title: String,
    val body: String,
    val at: Long,
    val item: MediaItem?,
    val art: String?,
    /** About something still to come: a season next week, a film on Friday. */
    val upcoming: Boolean = false,
)

/**
 * The website's notification centre, made of what the app already knows: an
 * episode out on a show you are caught up on, a season returning this month, a
 * film on your list releasing, a series you finished, and last month's recap.
 *
 * Nothing here is fetched for the inbox's sake - it reads what Home, the
 * library and the episode tracker already hold - and what you have read or
 * dismissed is remembered on the device.
 */
class InboxRepository(
    context: Context,
    library: StateFlow<Library>,
    progress: StateFlow<Map<Int, ShowProgress>>,
    airing: AiringRepository,
    scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("inbox", Context.MODE_PRIVATE)
    private val read = MutableStateFlow(prefs.getStringSet("read", emptySet()).orEmpty().toSet())
    private val dismissed = MutableStateFlow(prefs.getStringSet("dismissed", emptySet()).orEmpty().toSet())

    val events: StateFlow<List<InboxEvent>> = combine(
        library, progress, airing.latestUpNext, airing.latestReturning, dismissed,
    ) { lib, shows, upNext, returning, gone ->
        build(lib, shows, upNext, returning).filterNot { it.id in gone }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val readIds: StateFlow<Set<String>> = read

    val unread: StateFlow<Int> = combine(events, read) { list, seen -> list.count { it.id !in seen } }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    fun markRead(id: String) {
        if (id in read.value) return
        read.value = read.value + id
        prefs.edit().putStringSet("read", trim(read.value)).apply()
    }

    fun markAllRead() {
        read.value = read.value + events.value.map { it.id }
        prefs.edit().putStringSet("read", trim(read.value)).apply()
    }

    fun dismiss(id: String) {
        dismissed.value = dismissed.value + id
        prefs.edit().putStringSet("dismissed", trim(dismissed.value)).apply()
        markRead(id)
    }

    /** Ids are dated, so old ones can go; three hundred is months of events. */
    private fun trim(ids: Set<String>): Set<String> = ids.toList().takeLast(300).toSet()

    private fun build(
        lib: Library,
        shows: Map<Int, ShowProgress>,
        upNext: List<com.cineverse.app.data.airing.UpNextItem>,
        returning: List<com.cineverse.app.data.airing.ReturningItem>,
    ): List<InboxEvent> {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val events = mutableListOf<InboxEvent>()

        // An episode that is out, or out within a day, on a show you are caught up on.
        for (entry in upNext) {
            val dueSoon = entry.at - now <= 86_400_000L
            if (!dueSoon) continue
            val out = entry.at <= now
            events += InboxEvent(
                id = "ep:${entry.show.id}:${entry.next.season}:${entry.next.episode}",
                kind = InboxKind.Episode,
                title = entry.show.name,
                body = (if (out) "Out now · " else "Airs ${if (entry.exact) Airing.localTime(entry.at) else "today"} · ") +
                    "S${entry.next.season} E${entry.next.episode}" +
                    (entry.next.name.takeIf { it.isNotBlank() && !it.matches(Regex("Episode \\d+")) }?.let { " · $it" } ?: ""),
                at = minOf(entry.at, now),
                item = entry.show.asItem(),
                art = entry.next.still ?: entry.show.backdrop,
                upcoming = !out,
            )
        }

        // A season you were waiting for, this month.
        for (entry in returning) {
            val day = runCatching { LocalDate.parse(entry.airDate) }.getOrNull() ?: continue
            events += InboxEvent(
                id = "season:${entry.show.id}:${entry.season}",
                kind = InboxKind.Season,
                title = entry.show.name,
                body = if (entry.out) "Season ${entry.season} is out" else "Season ${entry.season} starts ${day.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))}",
                at = minOf(day.atStartOfDay(zone).toInstant().toEpochMilli(), now),
                item = entry.show.asItem(),
                art = entry.show.backdrop ?: entry.show.poster,
                upcoming = !entry.out,
            )
        }

        // A film on your list, released this past week or due in the next fortnight.
        for (saved in lib.saved.values) {
            if (saved.type != MediaType.Movie || lib.isWatched(saved.key)) continue
            val day = runCatching { LocalDate.parse(saved.releaseDate) }.getOrNull() ?: continue
            val days = ChronoUnit.DAYS.between(today, day)
            if (days !in -7..14) continue
            events += InboxEvent(
                id = "release:${saved.tmdbId}:${saved.releaseDate}",
                kind = InboxKind.Release,
                title = saved.title,
                body = when {
                    days < 0 -> "Released ${-days} day${if (days == -1L) "" else "s"} ago · on your list"
                    days == 0L -> "Released today · on your list"
                    days == 1L -> "Releases tomorrow"
                    else -> "Releases in $days days · ${day.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))}"
                },
                at = minOf(day.atStartOfDay(zone).toInstant().toEpochMilli(), now),
                item = saved.asItem(),
                art = saved.poster.ifBlank { null },
                upcoming = days > 0,
            )
        }

        // A series you finished in the last month.
        for (show in shows.values) {
            if (!show.complete || show.dropped) continue
            val at = YearModel.finishedAt(show)
            if (now - at > 30 * 86_400_000L || at <= 0) continue
            events += InboxEvent(
                id = "finished:${show.tmdbId}",
                kind = InboxKind.Finished,
                title = show.title,
                body = "Series complete · ${show.watchedCount} episodes. Your recap is on its page.",
                at = at,
                item = MediaItem(show.tmdbId, MediaType.Tv, show.title, show.poster.ifBlank { null }, show.backdrop.ifBlank { null }),
                art = show.backdrop.ifBlank { show.poster.ifBlank { null } },
            )
        }

        // Last month, on the first of this one, as the website's monthly recap.
        val last = today.minusMonths(1)
        val summary = YearModel.summary(lib, shows, last.year)
        val films = summary.films.count { film -> film.dates.any { YearModel.monthOf(it) == last.monthValue - 1 && YearModel.yearOf(it) == last.year } }
        val finished = summary.shows.count { YearModel.monthOf(it.finishedAt) == last.monthValue - 1 }
        if (films + finished > 0) {
            val month = last.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault()))
            events += InboxEvent(
                id = "recap:${last.year}-${last.monthValue}",
                kind = InboxKind.Recap,
                title = "Your $month",
                body = listOfNotNull(
                    films.takeIf { it > 0 }?.let { "$it film${if (it == 1) "" else "s"}" },
                    finished.takeIf { it > 0 }?.let { "$it series finished" },
                ).joinToString(" · "),
                at = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                item = null,
                art = null,
            )
        }

        return events.sortedByDescending { it.at }
    }
}
