package com.cineverse.app.data.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * TMDB's wire shapes. Every field is optional with a default, because TMDB
 * returns different subsets from different endpoints for the same object and a
 * missing field must never be an exception — the app would rather draw a card
 * with no year than crash a rail.
 */

@Serializable
data class PageDto<T>(
    val page: Int = 1,
    val results: List<T> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 1,
    @SerialName("total_results") val totalResults: Int = 0,
)

@Serializable
data class MediaDto(
    val id: Int = 0,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    val overview: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
    val popularity: Double = 0.0,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("original_language") val originalLanguage: String? = null,
    val adult: Boolean = false,
    @SerialName("known_for_department") val knownForDepartment: String? = null,
    /** A person result's best-known titles - what a search for a name is really after. */
    @SerialName("known_for") val knownFor: List<MediaDto> = emptyList(),
)

@Serializable
data class GenreDto(val id: Int = 0, val name: String = "")

@Serializable
data class CompanyDto(
    val id: Int = 0,
    val name: String = "",
    @SerialName("logo_path") val logoPath: String? = null,
    @SerialName("origin_country") val originCountry: String? = null,
)

@Serializable
data class CountryDto(
    @SerialName("iso_3166_1") val code: String = "",
    val name: String = "",
)

@Serializable
data class LanguageDto(
    @SerialName("iso_639_1") val code: String = "",
    val name: String = "",
    @SerialName("english_name") val englishName: String? = null,
)

@Serializable
data class VideoDto(
    val id: String = "",
    val key: String = "",
    val name: String = "",
    val site: String = "",
    val type: String = "",
    val official: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
)

@Serializable
data class VideosDto(val results: List<VideoDto> = emptyList())

@Serializable
data class CastDto(
    val id: Int = 0,
    val name: String = "",
    val character: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    val order: Int = 0,
)

@Serializable
data class CrewDto(
    val id: Int = 0,
    val name: String = "",
    val job: String? = null,
    val department: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
)

@Serializable
data class CreditsDto(
    val cast: List<CastDto> = emptyList(),
    val crew: List<CrewDto> = emptyList(),
)

@Serializable
data class ImageDto(
    @SerialName("file_path") val filePath: String = "",
    @SerialName("iso_639_1") val language: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    @SerialName("aspect_ratio") val aspectRatio: Double = 0.0,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
)

@Serializable
data class ImagesDto(
    val backdrops: List<ImageDto> = emptyList(),
    val posters: List<ImageDto> = emptyList(),
    val logos: List<ImageDto> = emptyList(),
)

@Serializable
data class ExternalIdsDto(
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("facebook_id") val facebookId: String? = null,
    @SerialName("instagram_id") val instagramId: String? = null,
    @SerialName("twitter_id") val twitterId: String? = null,
)

@Serializable
data class KeywordDto(val id: Int = 0, val name: String = "")

@Serializable
data class KeywordsDto(
    val keywords: List<KeywordDto> = emptyList(),
    val results: List<KeywordDto> = emptyList(),
) {
    /** Films call it `keywords`, series call it `results`. */
    val all: List<KeywordDto> get() = if (keywords.isNotEmpty()) keywords else results
}

@Serializable
data class CollectionRefDto(
    val id: Int = 0,
    val name: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
)

@Serializable
data class CollectionDto(
    val id: Int = 0,
    val name: String = "",
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val parts: List<MediaDto> = emptyList(),
)

// ---------- where to watch ----------

@Serializable
data class ProviderDto(
    @SerialName("provider_id") val providerId: Int = 0,
    @SerialName("provider_name") val providerName: String = "",
    @SerialName("logo_path") val logoPath: String? = null,
    @SerialName("display_priority") val displayPriority: Int = 0,
)

@Serializable
data class ProviderRegionDto(
    val link: String? = null,
    val flatrate: List<ProviderDto> = emptyList(),
    val rent: List<ProviderDto> = emptyList(),
    val buy: List<ProviderDto> = emptyList(),
    val ads: List<ProviderDto> = emptyList(),
    val free: List<ProviderDto> = emptyList(),
)

@Serializable
data class ProvidersDto(val results: Map<String, ProviderRegionDto> = emptyMap())

// ---------- certificates ----------

@Serializable
data class ReleaseDateDto(
    val certification: String = "",
    @SerialName("release_date") val releaseDate: String? = null,
    val type: Int = 0,
)

@Serializable
data class ReleaseDatesRegionDto(
    @SerialName("iso_3166_1") val country: String = "",
    @SerialName("release_dates") val releaseDates: List<ReleaseDateDto> = emptyList(),
)

@Serializable
data class ReleaseDatesDto(val results: List<ReleaseDatesRegionDto> = emptyList())

@Serializable
data class ContentRatingDto(
    @SerialName("iso_3166_1") val country: String = "",
    val rating: String = "",
)

@Serializable
data class ContentRatingsDto(val results: List<ContentRatingDto> = emptyList())

// ---------- reviews ----------

@Serializable
data class AuthorDetailsDto(
    val name: String? = null,
    val username: String? = null,
    @SerialName("avatar_path") val avatarPath: String? = null,
    val rating: Double? = null,
)

@Serializable
data class ReviewDto(
    val id: String = "",
    val author: String = "",
    @SerialName("author_details") val authorDetails: AuthorDetailsDto? = null,
    val content: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    val url: String? = null,
)

@Serializable
data class ReviewsDto(val results: List<ReviewDto> = emptyList())

// ---------- the big ones ----------

@Serializable
data class MovieDetailDto(
    val id: Int = 0,
    val title: String = "",
    @SerialName("original_title") val originalTitle: String? = null,
    val tagline: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val runtime: Int? = null,
    val status: String? = null,
    val budget: Long = 0,
    val revenue: Long = 0,
    val homepage: String? = null,
    val adult: Boolean = false,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
    val popularity: Double = 0.0,
    @SerialName("original_language") val originalLanguage: String? = null,
    val genres: List<GenreDto> = emptyList(),
    @SerialName("production_companies") val companies: List<CompanyDto> = emptyList(),
    @SerialName("production_countries") val countries: List<CountryDto> = emptyList(),
    @SerialName("spoken_languages") val languages: List<LanguageDto> = emptyList(),
    @SerialName("belongs_to_collection") val collection: CollectionRefDto? = null,
    val videos: VideosDto? = null,
    val credits: CreditsDto? = null,
    val images: ImagesDto? = null,
    val keywords: KeywordsDto? = null,
    @SerialName("external_ids") val externalIds: ExternalIdsDto? = null,
    @SerialName("release_dates") val releaseDates: ReleaseDatesDto? = null,
    @SerialName("watch/providers") val providers: ProvidersDto? = null,
    val recommendations: PageDto<MediaDto>? = null,
    val similar: PageDto<MediaDto>? = null,
    val reviews: ReviewsDto? = null,
)

@Serializable
data class SeasonRefDto(
    val id: Int = 0,
    @SerialName("season_number") val seasonNumber: Int = 0,
    val name: String = "",
    val overview: String? = null,
    @SerialName("episode_count") val episodeCount: Int = 0,
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
)

@Serializable
data class EpisodeDto(
    val id: Int = 0,
    @SerialName("episode_number") val episodeNumber: Int = 0,
    @SerialName("season_number") val seasonNumber: Int = 0,
    val name: String = "",
    val overview: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("still_path") val stillPath: String? = null,
    val runtime: Int? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
    /** "standard", "mid_season" or "finale" - how a finale is told from an episode. */
    @SerialName("episode_type") val episodeType: String? = null,
)

@Serializable
data class SeasonDto(
    val id: Int = 0,
    @SerialName("season_number") val seasonNumber: Int = 0,
    val name: String = "",
    val overview: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    val episodes: List<EpisodeDto> = emptyList(),
)

@Serializable
data class NetworkDto(
    val id: Int = 0,
    val name: String = "",
    @SerialName("logo_path") val logoPath: String? = null,
)

@Serializable
data class TvDetailDto(
    val id: Int = 0,
    val name: String = "",
    @SerialName("original_name") val originalName: String? = null,
    val tagline: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("last_air_date") val lastAirDate: String? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    val status: String? = null,
    val type: String? = null,
    @SerialName("in_production") val inProduction: Boolean = false,
    val homepage: String? = null,
    val adult: Boolean = false,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
    val popularity: Double = 0.0,
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int = 0,
    @SerialName("number_of_episodes") val numberOfEpisodes: Int = 0,
    val genres: List<GenreDto> = emptyList(),
    val networks: List<NetworkDto> = emptyList(),
    @SerialName("production_companies") val companies: List<CompanyDto> = emptyList(),
    @SerialName("production_countries") val countries: List<CountryDto> = emptyList(),
    @SerialName("spoken_languages") val languages: List<LanguageDto> = emptyList(),
    @SerialName("created_by") val createdBy: List<CastDto> = emptyList(),
    val seasons: List<SeasonRefDto> = emptyList(),
    @SerialName("next_episode_to_air") val nextEpisode: EpisodeDto? = null,
    @SerialName("last_episode_to_air") val lastEpisode: EpisodeDto? = null,
    val videos: VideosDto? = null,
    val credits: CreditsDto? = null,
    @SerialName("aggregate_credits") val aggregateCredits: CreditsDto? = null,
    val images: ImagesDto? = null,
    val keywords: KeywordsDto? = null,
    @SerialName("external_ids") val externalIds: ExternalIdsDto? = null,
    @SerialName("content_ratings") val contentRatings: ContentRatingsDto? = null,
    @SerialName("watch/providers") val providers: ProvidersDto? = null,
    val recommendations: PageDto<MediaDto>? = null,
    val similar: PageDto<MediaDto>? = null,
    val reviews: ReviewsDto? = null,
)

@Serializable
data class PersonCreditDto(
    val id: Int = 0,
    val title: String? = null,
    val name: String? = null,
    val character: String? = null,
    val job: String? = null,
    val department: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
    @SerialName("episode_count") val episodeCount: Int = 0,
    val popularity: Double = 0.0,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
)

@Serializable
data class CombinedCreditsDto(
    val cast: List<PersonCreditDto> = emptyList(),
    val crew: List<PersonCreditDto> = emptyList(),
)

@Serializable
data class PersonDetailDto(
    val id: Int = 0,
    val name: String = "",
    val biography: String? = null,
    val birthday: String? = null,
    val deathday: String? = null,
    @SerialName("place_of_birth") val placeOfBirth: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    @SerialName("known_for_department") val knownFor: String? = null,
    val popularity: Double = 0.0,
    val homepage: String? = null,
    @SerialName("combined_credits") val combinedCredits: CombinedCreditsDto? = null,
    val images: PersonImagesDto? = null,
    @SerialName("external_ids") val externalIds: ExternalIdsDto? = null,
)

@Serializable
data class PersonImagesDto(val profiles: List<ImageDto> = emptyList())
