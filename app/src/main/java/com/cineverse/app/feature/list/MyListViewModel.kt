package com.cineverse.app.feature.list

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cineverse.app.AppContainer
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.Genre
import com.cineverse.app.data.model.MediaFilter
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.model.SortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ListSegment(val label: String) {
    Watchlist("Watchlist"), Watching("Watching"), Watched("Watched")
}

/**
 * The orders a LIBRARY offers.
 *
 * "Best match" means recently added here rather than a relevance rank, because
 * there is no query to be relevant to — the default order of your own list is
 * the order you built it in.
 */
val ListSorts = listOf(
    SortOrder.Relevance,
    SortOrder.Title,
    SortOrder.Newest,
    SortOrder.Oldest,
    SortOrder.Rating,
    SortOrder.Imdb,
)

/**
 * The website's list sorts, all fifteen of them, in its order. "Recently
 * added" is a list's natural order; on Watched it reads as most recently
 * watched, and on Watching as most recently ticked.
 */
enum class ListSort(val label: String) {
    Recent("Recently added"),
    AddedOldest("Oldest added"),
    TitleAsc("Title A–Z"),
    TitleDesc("Title Z–A"),
    YearNew("Year (newest)"),
    YearOld("Year (oldest)"),
    RatingHigh("Community rating (highest)"),
    RatingLow("Community rating (lowest)"),
    MineHigh("My rating (highest)"),
    MineLow("My rating (lowest)"),
    RuntimeLong("Runtime (longest)"),
    RuntimeShort("Runtime (shortest)"),
    ImdbHigh("IMDb rating (highest)"),
    ImdbLow("IMDb rating (lowest)"),
    Smart("Smart blend"),
    ;

    fun labelFor(segment: ListSegment): String = when {
        this == Recent && segment == ListSegment.Watched -> "Recently watched"
        this == AddedOldest && segment == ListSegment.Watched -> "First watched"
        this == Recent && segment == ListSegment.Watching -> "Recently ticked"
        this == AddedOldest && segment == ListSegment.Watching -> "Longest untouched"
        else -> label
    }
}

/** The numbers at the top of the page. */
@Immutable
data class ListSummary(
    val titles: Int = 0,
    val films: Int = 0,
    val series: Int = 0,
    /** Minutes it would take to watch what is still unwatched here. */
    val minutesLeft: Int = 0,
    /** Up to three of the most recent posters, for the fan. */
    val posters: List<String> = emptyList(),
)

/** Where a show in progress is: the next episode and how far through you are. */
@Immutable
data class ShowPlace(val next: String, val fraction: Float)

/** A list's cover and the website's numbers under it. */
@Immutable
data class ListShowcase(
    val list: com.cineverse.app.data.model.UserList,
    /** Up to four posters, rotated by Shuffle cover. */
    val cover: List<String>,
    val canShuffle: Boolean,
    val count: Int,
    val films: Int,
    val series: Int,
    val avgRating: String,
    val avgRuntime: String,
    val topGenres: String,
    val years: String,
)

@Immutable
data class MyListState(
    val segment: ListSegment = ListSegment.Watchlist,
    val filter: MediaFilter = MediaFilter(),
    /** Everything in this segment, before the filter. */
    val all: List<MediaItem> = emptyList(),
    /** What the user actually sees. */
    val items: List<MediaItem> = emptyList(),
    val genres: List<Genre> = emptyList(),
    /** Which list is selected: "watchlist" or a custom list's id. */
    val listId: String = "watchlist",
    val sort: ListSort = ListSort.Recent,
    /** The selected list's collage and numbers, when one is selected. */
    val showcase: ListShowcase? = null,
    val summary: ListSummary = ListSummary(),
    /** Gemini's moods: the one chosen, and how many titles here carry each. */
    val mood: com.cineverse.app.data.ai.MoodTags.Mood? = null,
    val moods: List<Pair<com.cineverse.app.data.ai.MoodTags.Mood, Int>> = emptyList(),
    /** Shows in progress: where each one is. */
    val places: Map<String, ShowPlace> = emptyMap(),
    /** Grid of posters, or compact rows. */
    val rows: Boolean = false,
) {
    val filteredOut: Boolean get() = all.isNotEmpty() && items.isEmpty()
}

class MyListViewModel(private val app: AppContainer) : ViewModel() {

    private val segment = MutableStateFlow(ListSegment.Watchlist)
    private val filter = MutableStateFlow(MediaFilter())
    private val listId = MutableStateFlow("watchlist")
    private val mood = MutableStateFlow<com.cineverse.app.data.ai.MoodTags.Mood?>(null)
    private val viewPrefs = app.context.getSharedPreferences("my_list_view", android.content.Context.MODE_PRIVATE)
    private val rows = MutableStateFlow(viewPrefs.getBoolean("rows", false))
    private val genres = MutableStateFlow<List<Genre>>(emptyList())
    private val sort = MutableStateFlow(ListSort.Recent)
    private val coverPrefs = app.context.getSharedPreferences("list_covers", android.content.Context.MODE_PRIVATE)
    private val coverOffsets = MutableStateFlow(coverPrefs.all.mapValues { (it.value as? Int) ?: 0 })

    init {
        viewModelScope.launch {
            genres.value = (app.tmdb.genres(MediaType.Movie) + app.tmdb.genres(MediaType.Tv))
                .distinctBy { it.id }
                .sortedBy { it.name }
        }
        // Gemini's moods for anything on the list not tagged yet.
        viewModelScope.launch {
            app.library.library.collect { lib ->
                if (lib.loaded && app.settings.settings.value.geminiOn) runCatching { app.moodTags.tagMissing(lib.saved.values) }
            }
        }
    }

    val library: StateFlow<Library> = app.library.library

    val signedIn: StateFlow<Boolean> = app.auth.user
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, app.auth.signedIn)

    val state: StateFlow<MyListState> =
        combine(
            segment, filter, listId, genres,
            app.library.library, app.episodes.progress, app.unlockedLists.ids, sort, coverOffsets,
            mood, app.moodTags.tags, rows,
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val seg = values[0] as ListSegment
            @Suppress("UNCHECKED_CAST")
            val active = values[1] as MediaFilter
            val list = values[2] as String
            @Suppress("UNCHECKED_CAST")
            val genreList = values[3] as List<Genre>
            val lib = values[4] as Library
            @Suppress("UNCHECKED_CAST")
            val shows = values[5] as Map<Int, com.cineverse.app.data.model.ShowProgress>
            @Suppress("UNCHECKED_CAST")
            val open = values[6] as Set<String>
            val order = values[7] as ListSort
            @Suppress("UNCHECKED_CAST")
            val offsets = values[8] as Map<String, Int>
            val chosenMood = values[9] as com.cineverse.app.data.ai.MoodTags.Mood?
            @Suppress("UNCHECKED_CAST")
            val tags = values[10] as Map<String, Set<com.cineverse.app.data.ai.MoodTags.Mood>>
            val asRows = values[11] as Boolean
            // Lists whose PIN has not been entered this session.
            val locked = lib.lists.filter { it.hasPin && it.id !in open }.map { it.id }.toSet()

            val all = when (seg) {
                // EVERYTHING saved, watched or not.
                //
                // This used to hide anything already watched, which on a real
                // library meant 62 of 144 saved titles were invisible with no
                // way to reach them. The website shows all of them and offers
                // "unwatched only" as a FILTER -- which this app now has too,
                // in the filter sheet, where the user can see it is on.
                // One list at a time - the Watchlist itself, or one of yours.
                ListSegment.Watchlist -> lib.saved.values
                    .filter { it.lists.ifEmpty { listOf("watchlist") }.contains(list.ifBlank { "watchlist" }) }
                    .sortedByDescending { it.addedAt }
                    .map { it.asItem() }

                ListSegment.Watching -> shows.values
                    .filter { it.watchedCount > 0 && !it.complete && !it.dropped }
                    .sortedByDescending { it.log.lastOrNull()?.stamp ?: it.updatedAt }
                    .map {
                        MediaItem(
                            id = it.tmdbId,
                            type = MediaType.Tv,
                            title = it.title,
                            posterPath = it.poster.ifBlank { null },
                            backdropPath = it.backdrop.ifBlank { null },
                        )
                    }

                ListSegment.Watched -> lib.watched.values
                    .sortedByDescending { it.watchedAt }
                    .map { it.asItem() }
            }

            // "Watching" arrives in the order that matters -- most recently
            // ticked -- so the default order leaves it exactly as it is.
            val moodCounts = if (seg == ListSegment.Watchlist) {
                com.cineverse.app.data.ai.MoodTags.Mood.entries.map { m -> m to all.count { m in tags[it.key].orEmpty() } }.filter { it.second > 0 }
            } else emptyList()
            val moody = if (seg == ListSegment.Watchlist && chosenMood != null) all.filter { chosenMood in tags[it.key].orEmpty() } else all
            val filtered = active.copy(sort = SortOrder.Relevance)
                .apply(moody, isWatched = { lib.isWatched(it.key) }, imdbOf = ::imdbOf)
            val shown = sorted(filtered, order, seg, lib, shows)
            val selected = lib.lists.firstOrNull { it.id == list }
            MyListState(
                segment = seg,
                filter = active,
                all = all,
                items = shown,
                genres = genreList,
                listId = list,
                sort = order,
                showcase = if (seg == ListSegment.Watchlist && selected != null) {
                    showcase(selected, lib.saved.values.filter { it.lists.contains(selected.id) }, offsets[selected.id] ?: 0)
                } else null,
                summary = summary(all, lib, shows),
                mood = chosenMood.takeIf { seg == ListSegment.Watchlist },
                moods = moodCounts,
                places = if (seg == ListSegment.Watching) shows.values.associate { show ->
                    val next = show.nextUp()
                    "tv_${show.tmdbId}" to ShowPlace(
                        next = next?.let { (s, e) -> if (show.isAbsolute) "Episode $e next" else "S$s E$e next" } ?: "Caught up",
                        fraction = if (show.totalEpisodes > 0) show.watchedCount.toFloat() / show.totalEpisodes else 0f,
                    )
                } else emptyMap(),
                rows = asRows,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyListState())

    /**
     * A title whose score has not arrived sorts last rather than pretending to
     * be a zero -- the same honesty rule the website sort follows.
     */
    private fun imdbOf(item: MediaItem): Double {
        val imdbId = runCatching {
            app.tmdb.cachedDetail(item.id, item.type, app.settings.settings.value.region)?.imdbId
        }.getOrNull().orEmpty()
        if (imdbId.isBlank()) return -1.0
        return app.scores.cached(imdbId, item.type)?.imdb ?: -1.0
    }

    /**
     * The website's sorts. Each needs something the poster does not carry -
     * when it was added, its runtime, your own score - so they are looked up
     * from the library here rather than kept on every card.
     */
    private fun sorted(
        items: List<MediaItem>,
        order: ListSort,
        seg: ListSegment,
        lib: Library,
        shows: Map<Int, com.cineverse.app.data.model.ShowProgress>,
    ): List<MediaItem> {
        fun added(item: MediaItem): Long = when (seg) {
            ListSegment.Watchlist -> lib.saved[item.key]?.addedAt ?: 0L
            ListSegment.Watched -> lib.watched[item.key]?.lastPlay ?: 0L
            ListSegment.Watching -> shows[item.id]?.let { it.log.lastOrNull()?.stamp ?: it.updatedAt } ?: 0L
        }
        fun runtime(item: MediaItem): Int =
            (lib.saved[item.key]?.runtime ?: 0).takeIf { it > 0 }
                ?: (lib.watched[item.key]?.runtime ?: 0).takeIf { it > 0 }
                ?: (lib.watched[item.key]?.episodeRuntime ?: 0)
        fun year(item: MediaItem): Int = item.year.toIntOrNull() ?: 0
        fun mine(item: MediaItem): Int = lib.ratingOf(item.key)
        fun smart(item: MediaItem): Double =
            item.voteAverage * 0.6 + mine(item) * 0.4 + if (lib.isWatched(item.key)) 0.0 else 1.0
        return when (order) {
            ListSort.Recent -> items.sortedByDescending(::added)
            // A title with no date sorts last, not first.
            ListSort.AddedOldest -> items.sortedBy { added(it).takeIf { at -> at > 0 } ?: Long.MAX_VALUE }
            ListSort.TitleAsc -> items.sortedBy { it.title.lowercase() }
            ListSort.TitleDesc -> items.sortedByDescending { it.title.lowercase() }
            ListSort.YearNew -> items.sortedByDescending(::year)
            ListSort.YearOld -> items.sortedBy { year(it).takeIf { y -> y > 0 } ?: 9999 }
            ListSort.RatingHigh -> items.sortedByDescending { it.voteAverage }
            ListSort.RatingLow -> items.sortedBy { it.voteAverage }
            ListSort.MineHigh -> items.sortedByDescending(::mine)
            ListSort.MineLow -> items.sortedBy { mine(it).takeIf { m -> m > 0 } ?: 99 }
            ListSort.RuntimeLong -> items.sortedByDescending(::runtime)
            ListSort.RuntimeShort -> items.sortedBy { runtime(it).takeIf { r -> r > 0 } ?: Int.MAX_VALUE }
            ListSort.ImdbHigh -> items.sortedByDescending(::imdbOf)
            ListSort.ImdbLow -> items.sortedBy { imdbOf(it).takeIf { v -> v > 0 } ?: 99.0 }
            ListSort.Smart -> items.sortedByDescending(::smart)
        }
    }

    /** The website's showcase: a collage of four posters and the list's numbers. */
    private fun showcase(
        list: com.cineverse.app.data.model.UserList,
        members: List<com.cineverse.app.data.model.SavedItem>,
        offset: Int,
    ): ListShowcase {
        val posters = members.sortedByDescending { it.addedAt }.mapNotNull { it.poster.ifBlank { null } }
        val start = if (posters.isEmpty()) 0 else offset % posters.size
        val cover = List(minOf(4, posters.size)) { posters[(start + it) % posters.size] }
        val ratings = members.map { it.rating }.filter { it > 0 }
        val runtimes = members.map { it.runtime }.filter { it > 0 }
        val years = members.mapNotNull { it.year.toIntOrNull() }.filter { it in 1801..2199 }.sorted()
        val genres = members.flatMap { it.genres }.mapNotNull { com.cineverse.app.data.model.GenreNames[it] }
            .groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(3).joinToString(", ") { it.key }
        return ListShowcase(
            list = list,
            cover = cover,
            canShuffle = posters.size > 4,
            count = members.size,
            films = members.count { it.type == MediaType.Movie },
            series = members.count { it.type == MediaType.Tv },
            avgRating = if (ratings.isEmpty()) "-" else "%.1f".format(java.util.Locale.US, ratings.average()),
            avgRuntime = if (runtimes.isEmpty()) "-" else runtimes.average().toInt().let { m -> if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m" },
            topGenres = genres.ifBlank { "-" },
            years = when {
                years.isEmpty() -> "-"
                years.first() == years.last() -> "${years.first()}"
                else -> "${years.first()}–${years.last()}"
            },
        )
    }

    fun setSort(value: ListSort) { sort.value = value }

    fun setMood(value: com.cineverse.app.data.ai.MoodTags.Mood?) { mood.value = value }

    fun setRows(value: Boolean) {
        rows.value = value
        viewPrefs.edit().putBoolean("rows", value).apply()
    }

    /** Titles, the split, and the time it would take to watch what is left. */
    private fun summary(
        items: List<MediaItem>,
        lib: Library,
        shows: Map<Int, com.cineverse.app.data.model.ShowProgress>,
    ): ListSummary {
        var minutes = 0
        for (item in items) {
            if (lib.isWatched(item.key)) continue
            minutes += when (item.type) {
                MediaType.Movie -> (lib.saved[item.key]?.runtime ?: 0).takeIf { it > 0 } ?: 110
                MediaType.Tv -> shows[item.id]?.let { show ->
                    (show.totalEpisodes - show.watchedCount).coerceAtLeast(0) * (show.episodeRuntime.takeIf { it > 0 } ?: 42)
                } ?: 0
            }
        }
        return ListSummary(
            titles = items.size,
            films = items.count { it.type == MediaType.Movie },
            series = items.count { it.type == MediaType.Tv },
            minutesLeft = minutes,
            posters = items.mapNotNull { it.posterPath }.take(3),
        )
    }

    /** The next four posters on the cover, remembered on this device, as on the website. */
    fun shuffleCover(listId: String) {
        val next = (coverOffsets.value[listId] ?: 0) + 1
        coverOffsets.value = coverOffsets.value + (listId to next)
        coverPrefs.edit().putInt(listId, next).apply()
    }

    fun reorderLists(ids: List<String>) = viewModelScope.launch {
        runCatching { app.library.reorderLists(ids) }
    }

    fun select(value: ListSegment) { segment.value = value }

    fun setFilter(value: MediaFilter) { filter.value = value }

    fun selectList(id: String) { listId.value = id }

    // ---------- PIN locks ----------

    /** Lists opened with their PIN this session. */
    val unlocked: StateFlow<Set<String>> = app.unlockedLists.ids

    suspend fun verifyPin(list: com.cineverse.app.data.model.UserList, pin: String): Boolean =
        com.cineverse.app.data.lock.ListLocks.verify(pin, list.lockSalt, list.lockHash)

    fun unlock(list: com.cineverse.app.data.model.UserList) = app.unlockedLists.unlock(list.id)

    fun relock(list: com.cineverse.app.data.model.UserList) = app.unlockedLists.lock(list.id)

    suspend fun setPin(list: com.cineverse.app.data.model.UserList, pin: String): Boolean {
        val salt = com.cineverse.app.data.lock.ListLocks.newSalt()
        val hash = com.cineverse.app.data.lock.ListLocks.derive(pin, salt)
        val saved = app.library.saveListLock(list.id, salt, hash)
        // Setting a PIN leaves the list open for now - you just proved you
        // know it - exactly as the website does.
        if (saved) app.unlockedLists.unlock(list.id)
        // Its shows leave the lock-screen widgets now, not at their next redraw.
        if (saved) com.cineverse.app.widget.refreshShowWidgets(app.context)
        return saved
    }

    suspend fun removePin(list: com.cineverse.app.data.model.UserList): Boolean {
        val saved = app.library.saveListLock(list.id, null, null)
        if (saved) app.unlockedLists.lock(list.id)
        return saved
    }
}
