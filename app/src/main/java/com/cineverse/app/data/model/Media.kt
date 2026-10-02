package com.cineverse.app.data.model

import androidx.compose.runtime.Immutable

/** A film or a series. The app has no third kind; a person is not media. */
enum class MediaType(val wire: String) {
    Movie("movie"), Tv("tv");

    companion object {
        fun of(value: String?): MediaType = if (value == "tv") Tv else Movie
    }
}

/**
 * The one card model. Everything that draws a poster — a rail, a grid, a search
 * result, a franchise part, a person's filmography — draws one of these, so a
 * card looks and behaves identically everywhere in the app.
 *
 * [key] is `"$type_$id"`, the same key the website uses for a Firestore document
 * id, so a card can ask "am I saved?" without a lookup table.
 */
@Immutable
data class MediaItem(
    val id: Int,
    val type: MediaType,
    val title: String,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val overview: String = "",
    val voteAverage: Double = 0.0,
    val voteCount: Int = 0,
    val releaseDate: String = "",
    val genreIds: List<Int> = emptyList(),
    val popularity: Double = 0.0,
    val originalLanguage: String = "",
    val adult: Boolean = false,
) {
    val key: String get() = "${type.wire}_$id"
    val year: String get() = releaseDate.take(4)
    val hasArt: Boolean get() = !posterPath.isNullOrBlank()
}

@Immutable
data class Genre(val id: Int, val name: String)

@Immutable
data class Person(
    val id: Int,
    val name: String,
    val profilePath: String? = null,
    val character: String? = null,
    val job: String? = null,
)

@Immutable
data class Video(
    val key: String,
    val name: String,
    val type: String,
    val official: Boolean,
) {
    val isTrailer: Boolean get() = type.equals("Trailer", true)
    val thumbnail: String get() = "https://img.youtube.com/vi/$key/hqdefault.jpg"
}

/**
 * A studio or a network, with its mark.
 *
 * TMDB publishes a logo for most of them and the app was throwing it away,
 * keeping only the name. A row of names reads as a legal notice; the marks read
 * as provenance, which is what the information is actually for — "this is an
 * A24 film" or "this is on HBO" is a thing people decide by.
 *
 * Logos are monochrome PNGs with transparency, almost all of them dark ink
 * meant for a white page, so the strip tints them to the page's own text colour
 * rather than dropping them on a white plate the way the rest of the web does.
 */
@Immutable
data class Brand(
    val id: Int,
    val name: String,
    val logoPath: String?,
    /** A network broadcasts; a company makes. Shown in that order. */
    val isNetwork: Boolean,
)

@Immutable
data class WatchProvider(
    val id: Int,
    val name: String,
    val logoPath: String?,
    val kind: ProviderKind,
)

enum class ProviderKind { Stream, Rent, Buy, Free, Ads }

@Immutable
data class Episode(
    val id: Int,
    val season: Int,
    val number: Int,
    val name: String,
    val overview: String = "",
    val airDate: String = "",
    val stillPath: String? = null,
    val runtime: Int = 0,
    val voteAverage: Double = 0.0,
    val voteCount: Int = 0,
) {
    val rated: Boolean get() = voteCount > 0 && voteAverage > 0
    val label: String get() = "S$season E$number"

    /**
     * Has this episode actually been broadcast?
     *
     * A date in the future means no; NO DATE AT ALL also means no. TMDB files a
     * season ahead of its run with placeholder rows that have no air date and
     * often no name, and treating those as "aired but unwatched" is how a show
     * you are completely caught up on reports four episodes outstanding. The
     * date is compared in the device zone, which is the zone the viewer is in.
     */
    val hasAired: Boolean
        get() {
            if (airDate.isBlank()) return false
            val day = runCatching { java.time.LocalDate.parse(airDate) }.getOrNull()
                ?: return false
            return !day.isAfter(java.time.LocalDate.now())
        }

    /** A row TMDB has filed but not yet described: no name, no date, no still. */
    val isPlaceholder: Boolean
        get() = airDate.isBlank() && (name.isBlank() || name.matches(Regex("Episode \\d+")))
}

@Immutable
data class SeasonSummary(
    val number: Int,
    val name: String,
    val episodeCount: Int,
    val airDate: String,
    val posterPath: String?,
    val overview: String = "",
)

@Immutable
data class Review(
    val id: String,
    val author: String,
    val avatarPath: String?,
    val rating: Double?,
    val content: String,
    val createdAt: String,
)

/**
 * Everything a title page needs, from one request. Films and series are folded
 * into one shape here so the screen is written once: the fields a film has no
 * use for are simply empty.
 */
@Immutable
data class TitleDetail(
    val id: Int,
    val type: MediaType,
    val title: String,
    val originalTitle: String = "",
    val tagline: String = "",
    val overview: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val logoPath: String? = null,
    val releaseDate: String = "",
    val runtime: Int = 0,
    val status: String = "",
    val certificate: String = "",
    val voteAverage: Double = 0.0,
    val voteCount: Int = 0,
    val genres: List<Genre> = emptyList(),
    val originalLanguage: String = "",
    val spokenLanguages: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val companies: List<String> = emptyList(),
    val networks: List<String> = emptyList(),
    /** Studios and networks WITH their artwork, for the brand strip. */
    val brands: List<Brand> = emptyList(),
    val homepage: String = "",
    val imdbId: String = "",
    val adult: Boolean = false,
    val keywords: List<Genre> = emptyList(),
    val cast: List<Person> = emptyList(),
    val crew: List<Person> = emptyList(),
    val videos: List<Video> = emptyList(),
    val images: List<String> = emptyList(),
    val providers: List<WatchProvider> = emptyList(),
    val providerLink: String = "",
    val recommendations: List<MediaItem> = emptyList(),
    val reviews: List<Review> = emptyList(),
    // Films
    val budget: Long = 0,
    val revenue: Long = 0,
    val collectionId: Int = 0,
    val collectionName: String = "",
    val collectionPoster: String = "",
    val collectionBackdrop: String = "",
    // Series
    val seasons: List<SeasonSummary> = emptyList(),
    val episodeRuntime: Int = 0,
    val numberOfSeasons: Int = 0,
    val numberOfEpisodes: Int = 0,
    val nextEpisode: Episode? = null,
    val lastEpisode: Episode? = null,
    val inProduction: Boolean = false,
) {
    val key: String get() = "${type.wire}_$id"
    val year: String get() = releaseDate.take(4)
    val isSeries: Boolean get() = type == MediaType.Tv
    val director: Person? get() = crew.firstOrNull { it.job == "Director" }
    val trailer: Video?
        get() = videos.firstOrNull { it.isTrailer && it.official }
            ?: videos.firstOrNull { it.isTrailer }
            ?: videos.firstOrNull()

    fun asItem() = MediaItem(
        id = id, type = type, title = title, posterPath = posterPath,
        backdropPath = backdropPath, overview = overview, voteAverage = voteAverage,
        voteCount = voteCount, releaseDate = releaseDate,
        genreIds = genres.map { it.id }, originalLanguage = originalLanguage, adult = adult,
    )
}

@Immutable
data class PersonDetail(
    val id: Int,
    val name: String,
    val biography: String = "",
    val birthday: String = "",
    val deathday: String = "",
    val placeOfBirth: String = "",
    val profilePath: String? = null,
    val knownFor: String = "",
    val imdbId: String = "",
    val photos: List<String> = emptyList(),
    val asCast: List<CreditedItem> = emptyList(),
    val asCrew: List<CreditedItem> = emptyList(),
)

@Immutable
data class CreditedItem(
    val item: MediaItem,
    val role: String,
    val episodeCount: Int = 0,
)

/**
 * TMDB's genre ids, named. The same map the website carries in js/config.js —
 * kept as a constant rather than fetched because a card in a rail needs a genre
 * name before any network call has returned, and these ids have not changed in
 * a decade.
 */
val GenreNames: Map<Int, String> = mapOf(
    28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy",
    80 to "Crime", 99 to "Documentary", 18 to "Drama", 10751 to "Family",
    14 to "Fantasy", 36 to "History", 27 to "Horror", 10402 to "Music",
    9648 to "Mystery", 10749 to "Romance", 878 to "Sci-Fi", 10770 to "TV Movie",
    53 to "Thriller", 10752 to "War", 37 to "Western",
    10759 to "Action & Adventure", 10762 to "Kids", 10763 to "News",
    10764 to "Reality", 10765 to "Sci-Fi & Fantasy", 10766 to "Soap",
    10767 to "Talk", 10768 to "War & Politics",
)
