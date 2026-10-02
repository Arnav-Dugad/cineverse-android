package com.cineverse.app.data.model

import androidx.compose.runtime.Immutable

/** Films, series, or both. */
enum class TypeFilter(val label: String) {
    All("All"), Movie("Films"), Tv("Series");

    fun matches(item: MediaItem): Boolean = when (this) {
        All -> true
        Movie -> item.type == MediaType.Movie
        Tv -> item.type == MediaType.Tv
    }
}

/**
 * How a filtered list is ordered.
 *
 * [Relevance] means "leave it alone": a search already arrives in TMDB own
 * relevance order, and a library segment arrives in the order its segment is
 * about. Re-sorting either by default would throw away the one piece of ordering
 * that was free and correct.
 */
enum class SortOrder(val label: String) {
    Relevance("Best match"),
    Title("Title A-Z"),
    Newest("Newest first"),
    Oldest("Oldest first"),
    Rating("TMDB rating"),
    Imdb("IMDb rating"),
    Votes("Most voted"),
}

/**
 * One filter, shared by search and by the library.
 *
 * Deliberately a value rather than a set of flags scattered over two view
 * models: the same sheet drives both screens, so the same type has to describe
 * both, and "how many filters are on" has to be answerable in one place or the
 * chip badge will eventually disagree with the state it is counting.
 */
@Immutable
data class MediaFilter(
    val type: TypeFilter = TypeFilter.All,
    /** TMDB score floor, 0 meaning no floor. */
    val minRating: Int = 0,
    /** A TMDB genre id, 0 meaning any. */
    val genreId: Int = 0,
    /** The first year of a decade, 0 meaning any. */
    val decade: Int = 0,
    val sort: SortOrder = SortOrder.Relevance,
    val hideWatched: Boolean = false,
) {
    /** How many non-default choices are on — what the chip badge counts. */
    val activeCount: Int
        get() = listOf(
            type != TypeFilter.All,
            minRating > 0,
            genreId > 0,
            decade > 0,
            sort != SortOrder.Relevance,
            hideWatched,
        ).count { it }

    val isDefault: Boolean get() = activeCount == 0

    fun clear() = MediaFilter()

    /**
     * Filter, then sort.
     *
     * [imdbOf] is passed in rather than reached for: the IMDb number lives in a
     * cache that only the app container can see, and a model that has to know
     * about a repository to sort a list is a model that cannot be tested.
     */
    fun apply(
        items: List<MediaItem>,
        isWatched: (MediaItem) -> Boolean = { false },
        imdbOf: (MediaItem) -> Double = { -1.0 },
    ): List<MediaItem> {
        val kept = items.filter { item ->
            type.matches(item) &&
                (minRating == 0 || item.voteAverage >= minRating) &&
                (genreId == 0 || item.genreIds.contains(genreId)) &&
                (decade == 0 || item.yearOrNull()?.let { it >= decade && it < decade + 10 } == true) &&
                (!hideWatched || !isWatched(item))
        }
        return when (sort) {
            SortOrder.Relevance -> kept
            SortOrder.Title -> kept.sortedBy { it.title.lowercase() }
            SortOrder.Newest -> kept.sortedByDescending { it.yearOrNull() ?: Int.MIN_VALUE }
            SortOrder.Oldest -> kept.sortedBy { it.yearOrNull() ?: Int.MAX_VALUE }
            SortOrder.Rating -> kept.sortedByDescending { it.voteAverage }
            // A title whose score has not arrived sorts LAST rather than
            // pretending to be a zero, which is the website own rule and the
            // only honest way to sort a list that is still filling in.
            SortOrder.Imdb -> kept.sortedByDescending { imdbOf(it) }
            SortOrder.Votes -> kept.sortedByDescending { it.voteCount }
        }
    }

    companion object {
        /** The decades offered, newest first, back to the 1950s. */
        fun decades(now: Int = java.time.Year.now().value): List<Int> {
            val latest = (now / 10) * 10
            return generateSequence(latest) { it - 10 }.takeWhile { it >= 1950 }.toList()
        }
    }
}

private fun MediaItem.yearOrNull(): Int? = year.take(4).toIntOrNull()
