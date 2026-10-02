package com.cineverse.app.data.tmdb

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

/**
 * TMDB v3, as the app uses it.
 *
 * The api key is attached by an interceptor rather than named on every method,
 * and `append_to_response` is used hard: a title page is ONE request carrying
 * videos, credits, images, keywords, external ids, certificates, providers,
 * recommendations and reviews. The website makes several; the app makes one,
 * because on a phone every extra round trip is a visible stall.
 */
interface TmdbApi {

    @GET("trending/{media}/{window}")
    suspend fun trending(
        @Path("media") media: String,
        @Path("window") window: String = "week",
        @Query("page") page: Int = 1,
    ): PageDto<MediaDto>

    @GET("movie/{list}")
    suspend fun movieList(
        @Path("list") list: String,
        @Query("page") page: Int = 1,
        @Query("region") region: String? = null,
    ): PageDto<MediaDto>

    @GET("tv/{list}")
    suspend fun tvList(
        @Path("list") list: String,
        @Query("page") page: Int = 1,
    ): PageDto<MediaDto>

    @GET("discover/movie")
    suspend fun discoverMovies(@QueryMap params: Map<String, String>): PageDto<MediaDto>

    @GET("discover/tv")
    suspend fun discoverTv(@QueryMap params: Map<String, String>): PageDto<MediaDto>

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false,
    ): PageDto<MediaDto>

    @GET("search/{kind}")
    suspend fun search(
        @Path("kind") kind: String,
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false,
    ): PageDto<MediaDto>

    @GET("movie/{id}")
    suspend fun movie(
        @Path("id") id: Int,
        @Query("append_to_response") append: String = MOVIE_APPEND,
    ): MovieDetailDto

    @GET("tv/{id}")
    suspend fun tv(
        @Path("id") id: Int,
        @Query("append_to_response") append: String = TV_APPEND,
    ): TvDetailDto

    @GET("tv/{id}/season/{season}")
    suspend fun season(
        @Path("id") id: Int,
        @Path("season") season: Int,
    ): SeasonDto

    @GET("person/{id}")
    suspend fun person(
        @Path("id") id: Int,
        @Query("append_to_response") append: String = "combined_credits,images,external_ids",
    ): PersonDetailDto

    @GET("collection/{id}")
    suspend fun collection(@Path("id") id: Int): CollectionDto

    @GET("{kind}/{id}/external_ids")
    suspend fun externalIds(
        @Path("kind") kind: String,
        @Path("id") id: Int,
    ): ExternalIdsDto

    @GET("genre/{kind}/list")
    suspend fun genres(@Path("kind") kind: String): GenreListDto

    @GET("watch/providers/{kind}")
    suspend fun watchProviders(
        @Path("kind") kind: String,
        @Query("watch_region") region: String,
    ): ProviderListDto

    companion object {
        const val BASE_URL = "https://api.themoviedb.org/3/"

        /**
         * One title page, one request. `watch/providers` is the slash-bearing
         * key TMDB returns it under, which is why the DTO names it with a
         * @SerialName rather than a property called providers.
         */
        const val MOVIE_APPEND =
            "videos,credits,images,keywords,external_ids,release_dates,watch/providers,recommendations,similar,reviews"
        const val TV_APPEND =
            "videos,aggregate_credits,credits,images,keywords,external_ids,content_ratings,watch/providers,recommendations,similar,reviews"
    }
}

@kotlinx.serialization.Serializable
data class GenreListDto(val genres: List<GenreDto> = emptyList())

@kotlinx.serialization.Serializable
data class ProviderListDto(val results: List<ProviderDto> = emptyList())
