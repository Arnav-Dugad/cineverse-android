package com.cineverse.app.data.airing

import android.content.Context
import androidx.compose.runtime.Immutable
import com.cineverse.app.core.net.Http
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.model.ShowProgress
import com.cineverse.app.data.tmdb.TmdbRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * What is coming on the shows you watch, ported from the website's
 * js/up-next.js, js/returning.js and js/episode-times.js.
 */

/** The next episode TMDB has filed for a show. */
@Immutable
data class NextEpisode(
    val season: Int,
    val episode: Int,
    val name: String,
    val airDate: String,
    val still: String?,
    /** "standard", "mid_season", "finale". */
    val type: String,
)

/** The few facts the airing rails need about a show - a request with nothing appended. */
@Immutable
data class TvBrief(
    val id: Int,
    val name: String,
    val originalName: String,
    val poster: String?,
    val backdrop: String?,
    val status: String,
    val firstAirDate: String,
    /** season number -> its premiere date, "" when TMDB has none. */
    val seasonDates: Map<Int, String>,
    val next: NextEpisode?,
) {
    val ended: Boolean get() = status.lowercase() in setOf("ended", "canceled", "cancelled")

    fun asItem() = MediaItem(
        id = id, type = MediaType.Tv, title = name, posterPath = poster,
        backdropPath = backdrop, releaseDate = firstAirDate,
    )
}

/** One card of Up Next: a caught-up show and the episode it is waiting for. */
@Immutable
data class UpNextItem(
    val show: TvBrief,
    val next: NextEpisode,
    /** When it airs, as precisely as is known. */
    val at: Long,
    /** True when [at] is the broadcaster's own timestamp, not just a date. */
    val exact: Boolean,
) {
    val kind: String
        get() = when {
            next.episode == 1 -> "Season ${next.season} premiere"
            next.type == "finale" -> "Season finale"
            else -> "New episode"
        }
}

/** One card of Returning this month: a show you finished, back with a new season. */
@Immutable
data class ReturningItem(
    val show: TvBrief,
    val season: Int,
    val airDate: String,
    val out: Boolean,
) {
    fun badge(today: LocalDate = LocalDate.now()): String {
        if (out) return "S$season · Out now"
        val day = runCatching { LocalDate.parse(airDate) }.getOrNull() ?: return "S$season"
        val label = if (day == today.plusDays(1)) "Tomorrow"
        else day.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
        return "S$season · $label"
    }
}

object Airing {

    const val UP_NEXT_DAYS = 30L
    const val OUT_NOW_DAYS = 3L
    private const val DAY = 86_400_000L

    private fun localDayStart(date: String, zone: ZoneId = ZoneId.systemDefault()): Long? =
        runCatching { LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrNull()

    /**
     * Shows that could have something coming: started, caught up (nothing
     * aired is left unwatched), not dropped, and not ended for good. Most
     * recently touched first, and no more than [limit] of them, because each
     * one is a request.
     */
    fun candidates(shows: Map<Int, ShowProgress>, limit: Int = 24): List<ShowProgress> =
        shows.values
            .filter { !it.dropped && it.watchedCount > 0 && it.nextUp() == null }
            .filter { it.status.lowercase() !in setOf("ended", "canceled", "cancelled") }
            .sortedByDescending { it.log.maxOfOrNull { row -> row.stamp } ?: it.updatedAt }
            .take(limit)

    /** One Up Next card, or null when nothing is due inside the window. */
    fun upNext(show: TvBrief, airstamp: Long? = null, now: Long = System.currentTimeMillis()): UpNextItem? {
        val next = show.next ?: return null
        if (next.season <= 0 || next.episode <= 0) return null
        val day = localDayStart(next.airDate) ?: return null
        val exact = airstamp != null && airstamp > 0
        val at = if (exact) airstamp!! else day
        if (at - now > UP_NEXT_DAYS * DAY) return null
        val today = localDayStart(LocalDate.now().toString()) ?: now
        val late = if (exact) now - at else today - day
        if (late > OUT_NOW_DAYS * DAY) return null
        return UpNextItem(show, next, at, exact)
    }

    /**
     * What a countdown says right now. A broadcaster timestamp counts down to
     * the second; a bare date only says "Today", "Tomorrow", "In 3 days".
     */
    fun countdown(item: UpNextItem, now: Long = System.currentTimeMillis()): String {
        if (item.exact) {
            val ms = item.at - now
            if (ms <= 0) return "Out now"
            val total = ms / 1000
            val days = total / 86_400
            val hours = (total / 3_600) % 24
            val minutes = (total / 60) % 60
            val seconds = total % 60
            val clock = "%02d:%02d:%02d".format(Locale.US, hours, minutes, seconds)
            return if (days > 0) "${days}d $clock" else clock
        }
        val day = localDayStart(item.next.airDate) ?: return ""
        val today = localDayStart(LocalDate.now().toString()) ?: now
        val days = ((day - today) / DAY).toInt()
        return when {
            days < 0 -> "Out now"
            days == 0 -> "Today"
            days == 1 -> "Tomorrow"
            else -> "In $days days"
        }
    }

    /** "Tue 7 Oct, 6:30 am" in the viewer's own zone. */
    fun localTime(at: Long): String =
        Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.getDefault()))

    /**
     * Shows you have finished or caught up on, whose next season premieres
     * THIS calendar month - the website's "Returning this month".
     */
    fun returning(show: TvBrief, lastSeason: Int, today: LocalDate = LocalDate.now()): ReturningItem? {
        if (show.ended) return null
        val month = "%04d-%02d".format(Locale.US, today.year, today.monthValue)
        val floor = maxOf(lastSeason, 1)
        val candidates = show.seasonDates
            .filter { (season, date) -> season > floor && date.startsWith(month) }
            .map { (season, date) -> season to date }
            .toMutableList()
        val next = show.next
        if (next != null && next.episode == 1 && next.season > floor && next.airDate.startsWith(month) &&
            candidates.none { it.first == next.season }
        ) candidates += next.season to next.airDate
        val (season, date) = candidates.minByOrNull { it.first } ?: return null
        return ReturningItem(show, season, date, out = date <= today.toString())
    }
}

/**
 * Exact air times from TVmaze's free public API.
 *
 * TMDB knows the DATE an episode airs; TVmaze knows the minute, with the
 * broadcaster's offset, so "airs tonight" can say what time tonight in the
 * viewer's own zone. A match has to agree on title, premiere year (within one)
 * and the episode itself, or it is not used: a wrong time is worse than a date.
 *
 * Cached on the device for a week when found and a day when not, and limited to
 * TVmaze's published rate (twenty calls per ten seconds) with room to spare.
 */
class ExactTimes(context: Context, private val http: OkHttpClient) {

    private val prefs = context.getSharedPreferences("episode_times", Context.MODE_PRIVATE)
    private val gate = Semaphore(3)
    private val budget = Mutex()
    private val stamps = ArrayDeque<Long>()

    private fun key(showId: Int, season: Int, episode: Int) = "$showId:$season:$episode"

    /** TVmaze's still for an episode, when it published one with the time. */
    fun cachedImage(showId: Int, season: Int, episode: Int): String? =
        prefs.getString("img:" + key(showId, season, episode), null)?.takeIf { it.isNotBlank() }

    /**
     * A series' episode length in minutes from TVmaze, for the shows TMDB
     * leaves blank - without it every episode was counted as 42 minutes, so
     * a half-hour sitcom doubled your hours and a long drama halved them.
     * Matched by IMDb id first (exact), then by name and premiere year.
     * Remembered for a month when found, a week when not.
     */
    suspend fun runtime(tmdbId: Int, imdbId: String, name: String, firstAirDate: String): Int {
        val key = "rt:$tmdbId"
        prefs.getString(key, null)?.let { raw ->
            val minutes = raw.substringBefore('|').toIntOrNull() ?: 0
            val checked = raw.substringAfter('|').toLongOrNull() ?: 0
            val ttl = if (minutes > 0) 30 * 86_400_000L else 7 * 86_400_000L
            if (System.currentTimeMillis() - checked < ttl) return minutes
        }
        val found = gate.withPermit {
            throttle()
            withContext(Dispatchers.IO) {
                val urls = buildList {
                    if (imdbId.isNotBlank()) add("https://api.tvmaze.com/lookup/shows?imdb=$imdbId")
                    add("https://api.tvmaze.com/singlesearch/shows?q=" + java.net.URLEncoder.encode(name, "UTF-8"))
                }
                for ((index, url) in urls.withIndex()) {
                    val minutes = runCatching {
                        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                            if (!response.isSuccessful) return@use null
                            val root = Http.json.parseToJsonElement(response.body.string()).jsonObject
                            // A name search must agree on the premiere year.
                            if (index > 0 || imdbId.isBlank()) {
                                val mazeYear = root["premiered"]?.jsonPrimitive?.content?.take(4)?.toIntOrNull()
                                val tmdbYear = firstAirDate.take(4).toIntOrNull()
                                if (mazeYear != null && tmdbYear != null && kotlin.math.abs(mazeYear - tmdbYear) > 1) return@use 0
                            }
                            (root["averageRuntime"]?.jsonPrimitive?.intOrNull ?: root["runtime"]?.jsonPrimitive?.intOrNull ?: 0)
                        }
                    }.getOrNull()
                    if (minutes != null && minutes > 0) return@withContext minutes
                    if (minutes == null) return@withContext null
                }
                0
            }
        }
        if (found != null) prefs.edit().putString(key, "$found|${System.currentTimeMillis()}").apply()
        return found ?: 0
    }

    /** The cached answer only - no request. Zero when unknown or none. */
    fun cached(showId: Int, season: Int, episode: Int): Long {
        val raw = prefs.getString(key(showId, season, episode), null) ?: return 0
        return raw.substringBefore('|').toLongOrNull() ?: 0
    }

    suspend fun lookup(show: TvBrief): Long? {
        val next = show.next ?: return null
        if (next.airDate.isBlank()) return null
        val key = key(show.id, next.season, next.episode)
        prefs.getString(key, null)?.let { raw ->
            val stamp = raw.substringBefore('|').toLongOrNull() ?: 0
            val checked = raw.substringAfter('|').toLongOrNull() ?: 0
            val ttl = if (stamp > 0) 7 * 86_400_000L else 86_400_000L
            if (System.currentTimeMillis() - checked < ttl) return stamp.takeIf { it > 0 }
        }
        val found = gate.withPermit { throttle(); fetch(show) }
        // A network failure is not remembered: only a real answer, either way.
        if (found != null) {
            prefs.edit().putString(key, "${found}|${System.currentTimeMillis()}").apply()
        }
        return found?.takeIf { it > 0 }
    }

    /** Sixteen calls in any ten seconds, which keeps clear of TVmaze's limit. */
    private suspend fun throttle() {
        while (true) {
            val wait = budget.withLock {
                val now = System.currentTimeMillis()
                while (stamps.isNotEmpty() && now - stamps.first() > 10_000) stamps.removeFirst()
                if (stamps.size < 16) { stamps.addLast(now); 0L } else 10_050 - (now - stamps.first())
            }
            if (wait <= 0) return
            delay(wait)
        }
    }

    /** The airstamp in epoch millis, 0 for "TVmaze has no match", null for a failed call. */
    private suspend fun fetch(show: TvBrief): Long? = withContext(Dispatchers.IO) {
        val next = show.next ?: return@withContext 0L
        val url = "https://api.tvmaze.com/singlesearch/shows?q=" +
            java.net.URLEncoder.encode(show.name, "UTF-8") + "&embed=nextepisode"
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (response.code == 404) return@runCatching 0L
                if (!response.isSuccessful) return@runCatching null
                val root = Http.json.parseToJsonElement(response.body.string()).jsonObject
                val name = root["name"]?.jsonPrimitive?.content.orEmpty()
                val premiered = root["premiered"]?.jsonPrimitive?.content.orEmpty()
                val episode = root["_embedded"]?.jsonObject?.get("nextepisode")?.jsonObject
                    ?: return@runCatching 0L
                val titleMatches = clean(name) == clean(show.name) || clean(name) == clean(show.originalName)
                val tmdbYear = show.firstAirDate.take(4).toIntOrNull()
                val mazeYear = premiered.take(4).toIntOrNull()
                val yearMatches = tmdbYear == null || mazeYear == null || kotlin.math.abs(tmdbYear - mazeYear) <= 1
                val episodeMatches = episode["season"]?.jsonPrimitive?.intOrNull == next.season &&
                    episode["number"]?.jsonPrimitive?.intOrNull == next.episode
                val airtime = episode["airtime"]?.jsonPrimitive?.content.orEmpty()
                if (titleMatches && yearMatches && episodeMatches) {
                    // The episode's own still, when TVmaze has one before TMDB does.
                    val image = (episode["image"] as? kotlinx.serialization.json.JsonObject)
                        ?.let { it["original"]?.jsonPrimitive?.content ?: it["medium"]?.jsonPrimitive?.content }
                        ?.replace("http://", "https://")
                    if (!image.isNullOrBlank()) prefs.edit().putString("img:" + key(show.id, next.season, next.episode), image).apply()
                }
                val stamp = episode["airstamp"]?.jsonPrimitive?.content.orEmpty()
                if (titleMatches && yearMatches && episodeMatches && airtime.isNotBlank() && stamp.isNotBlank()) {
                    runCatching { java.time.OffsetDateTime.parse(stamp).toInstant().toEpochMilli() }.getOrDefault(0L)
                } else 0L
            }
        }.getOrNull()
    }

    private fun clean(value: String) =
        value.lowercase().replace("&", "and").replace(Regex("[^a-z0-9]+"), " ").trim()
}

/**
 * Up Next and Returning, resolved together: both read the same show briefs,
 * so one pass over the library serves both rails.
 */
class AiringRepository(
    private val tmdb: TmdbRepository,
    val times: ExactTimes,
) {
    /**
     * One budget for both rails, and a small one. These are background
     * requests: the screen the user is looking at must never wait behind them
     * in the HTTP client's per-host queue.
     */
    private val gate = Semaphore(3)

    private val _latestUpNext = kotlinx.coroutines.flow.MutableStateFlow<List<UpNextItem>>(emptyList())
    private val _latestReturning = kotlinx.coroutines.flow.MutableStateFlow<List<ReturningItem>>(emptyList())

    /** The last answers, for the inbox to read without asking TMDB again. */
    val latestUpNext: kotlinx.coroutines.flow.StateFlow<List<UpNextItem>> = _latestUpNext
    val latestReturning: kotlinx.coroutines.flow.StateFlow<List<ReturningItem>> = _latestReturning

    suspend fun upNext(shows: Map<Int, ShowProgress>): List<UpNextItem> {
        val briefs = coroutineScope {
            Airing.candidates(shows).map { show -> async { gate.withPermit { tmdb.tvBrief(show.tmdbId) } } }.awaitAll()
        }.filterNotNull()
        val items = coroutineScope {
            briefs.map { brief ->
                async {
                    val base = Airing.upNext(brief) ?: return@async null
                    val stamp = runCatching { times.lookup(brief) }.getOrNull()
                    if (stamp != null) Airing.upNext(brief, stamp) else base
                }
            }.awaitAll()
        }.filterNotNull()
        return items.sortedWith(compareBy<UpNextItem> { it.at }.thenBy { it.show.name })
            .also { _latestUpNext.value = it }
    }

    suspend fun returning(shows: Map<Int, ShowProgress>, watchedShows: List<Int>): List<ReturningItem> {
        // Finished or caught up, plus series marked watched without tracking.
        val tracked = shows.values
            .filter { !it.dropped && it.watchedCount > 0 && it.nextUp() == null }
            .associate { it.tmdbId to (it.seasons.filterValues { e -> e.isNotEmpty() }.keys.maxOrNull() ?: 0) }
        val untracked = watchedShows.filter { it !in shows }.associateWith { 0 }
        val all = (tracked + untracked).entries.take(40)
        return coroutineScope {
            all.map { (id, last) ->
                async { gate.withPermit { tmdb.tvBrief(id)?.let { Airing.returning(it, last) } } }
            }.awaitAll()
        }.filterNotNull().sortedWith(compareBy<ReturningItem> { it.airDate }.thenBy { it.show.name })
            .also { _latestReturning.value = it }
    }
}
