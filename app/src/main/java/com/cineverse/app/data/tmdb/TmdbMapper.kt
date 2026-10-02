package com.cineverse.app.data.tmdb

import com.cineverse.app.data.model.CreditedItem
import com.cineverse.app.data.model.Episode
import com.cineverse.app.data.model.Genre
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.model.Person
import com.cineverse.app.data.model.PersonDetail
import com.cineverse.app.data.model.ProviderKind
import com.cineverse.app.data.model.Review
import com.cineverse.app.data.model.SeasonSummary
import com.cineverse.app.data.model.TitleDetail
import com.cineverse.app.data.model.Video
import com.cineverse.app.data.model.WatchProvider

/**
 * Wire shapes to the app's own. Everything fallible lives here, so no screen
 * ever has to reason about a null `title` or a `media_type` TMDB forgot to send.
 */

fun MediaDto.toItem(fallbackType: MediaType? = null): MediaItem? {
    val type = when {
        mediaType == "movie" -> MediaType.Movie
        mediaType == "tv" -> MediaType.Tv
        mediaType == "person" -> return null
        fallbackType != null -> fallbackType
        // No media_type and no hint: a first_air_date is the giveaway.
        !firstAirDate.isNullOrBlank() -> MediaType.Tv
        else -> MediaType.Movie
    }
    val name = title ?: this.name ?: return null
    if (id == 0 || name.isBlank()) return null
    return MediaItem(
        id = id,
        type = type,
        title = name,
        posterPath = posterPath,
        backdropPath = backdropPath,
        overview = overview.orEmpty(),
        voteAverage = voteAverage,
        voteCount = voteCount,
        releaseDate = (releaseDate ?: firstAirDate).orEmpty(),
        genreIds = genreIds,
        popularity = popularity,
        originalLanguage = originalLanguage.orEmpty(),
        adult = adult,
    )
}

fun List<MediaDto>.toItems(fallbackType: MediaType? = null): List<MediaItem> =
    mapNotNull { it.toItem(fallbackType) }

/**
 * The title logo, picked exactly as the website picks it: an English logo first,
 * then a language-neutral one, then whatever there is. A logo in the wrong
 * language is worse than no logo, because the title text underneath is right.
 */
private fun pickLogo(images: ImagesDto?): String? {
    val logos = images?.logos.orEmpty()
    if (logos.isEmpty()) return null
    return (logos.firstOrNull { it.language == "en" }
        ?: logos.firstOrNull { it.language == null }
        ?: logos.first()).filePath.takeIf { it.isNotBlank() }
}

/**
 * The age certificate for the viewer's own region, falling back to the US only
 * when TMDB has nothing — the same rule as the website, which had the two
 * surfaces disagreeing until it was written down once.
 */
private fun movieCertificate(dto: MovieDetailDto, region: String): String {
    val rows = dto.releaseDates?.results.orEmpty()
    fun find(country: String) = rows.firstOrNull { it.country == country }
        ?.releaseDates?.map { it.certification }?.firstOrNull { it.isNotBlank() }.orEmpty()
    return find(region.uppercase()).ifBlank { find("US") }
}

private fun tvCertificate(dto: TvDetailDto, region: String): String {
    val rows = dto.contentRatings?.results.orEmpty()
    fun find(country: String) = rows.firstOrNull { it.country == country }?.rating.orEmpty()
    return find(region.uppercase()).ifBlank { find("US") }
}

private fun providersFor(dto: ProvidersDto?, region: String): Pair<List<WatchProvider>, String> {
    val entry = dto?.results?.get(region.uppercase()) ?: return emptyList<WatchProvider>() to ""
    fun map(list: List<ProviderDto>, kind: ProviderKind) =
        list.map { WatchProvider(it.providerId, it.providerName, it.logoPath, kind) }
    val all = map(entry.flatrate, ProviderKind.Stream) +
        map(entry.free, ProviderKind.Free) +
        map(entry.ads, ProviderKind.Ads) +
        map(entry.rent, ProviderKind.Rent) +
        map(entry.buy, ProviderKind.Buy)
    // One logo per service: a service that both streams and rents is one row,
    // and the better offer wins because Stream is first in the list above.
    return all.distinctBy { it.id } to entry.link.orEmpty()
}

private fun videosOf(dto: VideosDto?): List<Video> =
    dto?.results.orEmpty()
        .filter { it.site.equals("YouTube", true) && it.key.isNotBlank() }
        .sortedWith(compareByDescending<VideoDto> { it.type.equals("Trailer", true) }
            .thenByDescending { it.official })
        .map { Video(it.key, it.name, it.type, it.official) }

private fun castOf(credits: CreditsDto?): List<Person> =
    credits?.cast.orEmpty().sortedBy { it.order }
        .map { Person(it.id, it.name, it.profilePath, character = it.character) }

private fun crewOf(credits: CreditsDto?): List<Person> =
    credits?.crew.orEmpty().map { Person(it.id, it.name, it.profilePath, job = it.job) }

private fun reviewsOf(dto: ReviewsDto?): List<Review> =
    dto?.results.orEmpty().map {
        Review(
            id = it.id,
            author = it.authorDetails?.name?.takeIf(String::isNotBlank) ?: it.author,
            avatarPath = it.authorDetails?.avatarPath,
            rating = it.authorDetails?.rating,
            content = it.content,
            createdAt = it.createdAt.orEmpty(),
        )
    }

private fun stillsOf(images: ImagesDto?): List<String> =
    images?.backdrops.orEmpty().sortedByDescending { it.voteAverage }
        .map { it.filePath }.filter { it.isNotBlank() }.take(24)

fun MovieDetailDto.toDetail(region: String): TitleDetail {
    val (providers, link) = providersFor(providers, region)
    return TitleDetail(
        id = id,
        type = MediaType.Movie,
        title = title,
        originalTitle = originalTitle.orEmpty(),
        tagline = tagline.orEmpty(),
        overview = overview.orEmpty(),
        posterPath = posterPath,
        backdropPath = backdropPath,
        logoPath = pickLogo(images),
        releaseDate = releaseDate.orEmpty(),
        runtime = runtime ?: 0,
        status = status.orEmpty(),
        certificate = movieCertificate(this, region),
        voteAverage = voteAverage,
        voteCount = voteCount,
        genres = genres.map { Genre(it.id, it.name) },
        originalLanguage = originalLanguage.orEmpty(),
        spokenLanguages = languages.map { it.englishName ?: it.name }.filter { it.isNotBlank() },
        countries = countries.map { it.name },
        companies = companies.map { it.name },
        brands = brandsOf(emptyList(), companies),
        homepage = homepage.orEmpty(),
        imdbId = externalIds?.imdbId.orEmpty(),
        adult = adult,
        keywords = keywords?.all.orEmpty().map { Genre(it.id, it.name) },
        cast = castOf(credits),
        crew = crewOf(credits),
        videos = videosOf(videos),
        images = stillsOf(images),
        providers = providers,
        providerLink = link,
        recommendations = (recommendations?.results.orEmpty() + similar?.results.orEmpty())
            .toItems(MediaType.Movie).distinctBy { it.id },
        reviews = reviewsOf(reviews),
        budget = budget,
        revenue = revenue,
        collectionId = collection?.id ?: 0,
        collectionName = collection?.name.orEmpty(),
        collectionPoster = collection?.posterPath.orEmpty(),
        collectionBackdrop = collection?.backdropPath.orEmpty(),
    )
}

fun TvDetailDto.toDetail(region: String): TitleDetail {
    val (providers, link) = providersFor(providers, region)
    // Series credits: `aggregate_credits` is the real cast of a show (everyone,
    // with episode counts); `credits` is only the latest season's. Prefer the
    // former and fall back, because a long-running show's cast list is wrong
    // otherwise.
    val creditSource = aggregateCredits?.takeIf { it.cast.isNotEmpty() } ?: credits
    return TitleDetail(
        id = id,
        type = MediaType.Tv,
        title = name,
        originalTitle = originalName.orEmpty(),
        tagline = tagline.orEmpty(),
        overview = overview.orEmpty(),
        posterPath = posterPath,
        backdropPath = backdropPath,
        logoPath = pickLogo(images),
        releaseDate = firstAirDate.orEmpty(),
        runtime = episodeRunTime.firstOrNull() ?: 0,
        status = status.orEmpty(),
        certificate = tvCertificate(this, region),
        voteAverage = voteAverage,
        voteCount = voteCount,
        genres = genres.map { Genre(it.id, it.name) },
        originalLanguage = originalLanguage.orEmpty(),
        spokenLanguages = languages.map { it.englishName ?: it.name }.filter { it.isNotBlank() },
        countries = countries.map { it.name },
        companies = companies.map { it.name },
        networks = networks.map { it.name },
        brands = brandsOf(networks, companies),
        homepage = homepage.orEmpty(),
        imdbId = externalIds?.imdbId.orEmpty(),
        adult = adult,
        keywords = keywords?.all.orEmpty().map { Genre(it.id, it.name) },
        cast = castOf(creditSource),
        crew = crewOf(creditSource) + createdBy.map { Person(it.id, it.name, it.profilePath, job = "Creator") },
        videos = videosOf(videos),
        images = stillsOf(images),
        providers = providers,
        providerLink = link,
        recommendations = (recommendations?.results.orEmpty() + similar?.results.orEmpty())
            .toItems(MediaType.Tv).distinctBy { it.id },
        reviews = reviewsOf(reviews),
        // Specials (season 0) are left out everywhere in CineVerse: they are not
        // part of a show's run and counting them makes every progress figure lie.
        seasons = seasons.filter { it.seasonNumber > 0 && it.episodeCount > 0 }
            .map {
                SeasonSummary(
                    number = it.seasonNumber,
                    name = it.name.ifBlank { "Season ${it.seasonNumber}" },
                    episodeCount = it.episodeCount,
                    airDate = it.airDate.orEmpty(),
                    posterPath = it.posterPath,
                    overview = it.overview.orEmpty(),
                )
            },
        episodeRuntime = episodeRunTime.firstOrNull() ?: 0,
        numberOfSeasons = numberOfSeasons,
        numberOfEpisodes = numberOfEpisodes,
        nextEpisode = nextEpisode?.toEpisode(),
        lastEpisode = lastEpisode?.toEpisode(),
        inProduction = inProduction,
    )
}

fun EpisodeDto.toEpisode() = Episode(
    id = id,
    season = seasonNumber,
    number = episodeNumber,
    name = name.ifBlank { "Episode $episodeNumber" },
    overview = overview.orEmpty(),
    airDate = airDate.orEmpty(),
    stillPath = stillPath,
    runtime = runtime ?: 0,
    voteAverage = voteAverage,
    voteCount = voteCount,
)

fun PersonDetailDto.toDetail(): PersonDetail {
    fun credit(dto: PersonCreditDto, role: String): CreditedItem? {
        val type = MediaType.of(dto.mediaType)
        val name = dto.title ?: dto.name ?: return null
        if (dto.id == 0 || name.isBlank()) return null
        return CreditedItem(
            item = MediaItem(
                id = dto.id, type = type, title = name,
                posterPath = dto.posterPath, backdropPath = dto.backdropPath,
                voteAverage = dto.voteAverage, voteCount = dto.voteCount,
                releaseDate = (dto.releaseDate ?: dto.firstAirDate).orEmpty(),
                genreIds = dto.genreIds, popularity = dto.popularity,
            ),
            role = role,
            episodeCount = dto.episodeCount,
        )
    }
    val byNewest = compareByDescending<CreditedItem> { it.item.releaseDate }
    return PersonDetail(
        id = id,
        name = name,
        biography = biography.orEmpty(),
        birthday = birthday.orEmpty(),
        deathday = deathday.orEmpty(),
        placeOfBirth = placeOfBirth.orEmpty(),
        profilePath = profilePath,
        knownFor = knownFor.orEmpty(),
        imdbId = externalIds?.imdbId.orEmpty(),
        photos = images?.profiles.orEmpty().map { it.filePath }.filter { it.isNotBlank() },
        asCast = combinedCredits?.cast.orEmpty()
            .mapNotNull { credit(it, it.character.orEmpty()) }
            .distinctBy { it.item.key }.sortedWith(byNewest),
        asCrew = combinedCredits?.crew.orEmpty()
            .mapNotNull { credit(it, it.job.orEmpty()) }
            .distinctBy { it.item.key + it.role }.sortedWith(byNewest),
    )
}

/**
 * Networks first, then studios, each once.
 *
 * Capped at six: TMDB lists every co-production shell on some films, and a
 * fourteen-logo strip is noise rather than provenance. Anything without a logo
 * is dropped — a name in a row of marks looks like a loading failure.
 */
private fun brandsOf(
    networks: List<NetworkDto>,
    companies: List<CompanyDto>,
): List<com.cineverse.app.data.model.Brand> {
    val seen = mutableSetOf<Int>()
    return buildList {
        for (network in networks) {
            if (network.logoPath.isNullOrBlank() || !seen.add(network.id)) continue
            add(
                com.cineverse.app.data.model.Brand(
                    id = network.id,
                    name = network.name,
                    logoPath = network.logoPath,
                    isNetwork = true,
                )
            )
        }
        for (company in companies) {
            if (company.logoPath.isNullOrBlank() || !seen.add(company.id)) continue
            add(
                com.cineverse.app.data.model.Brand(
                    id = company.id,
                    name = company.name,
                    logoPath = company.logoPath,
                    isNetwork = false,
                )
            )
        }
    }.take(6)
}
