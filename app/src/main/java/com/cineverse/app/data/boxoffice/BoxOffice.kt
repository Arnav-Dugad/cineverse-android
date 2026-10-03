package com.cineverse.app.data.boxoffice

import android.icu.text.NumberFormat
import android.icu.util.ULocale
import androidx.compose.runtime.Immutable
import com.cineverse.app.core.net.Http
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.tmdb.TmdbRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.util.Locale

/**
 * Worldwide box office, ported from the website's js/box-office.js.
 *
 * TMDB can SORT discover results by revenue but does not include the amount in
 * those rows, so every page is hydrated with each film's own detail record, a
 * few at a time. Every figure on screen is therefore the reported value, never
 * a guess — and a film TMDB has no revenue for is left out rather than ranked
 * at zero.
 */
@Immutable
data class FilmMoney(
    val id: Int,
    val title: String,
    val poster: String? = null,
    val backdrop: String? = null,
    val releaseDate: String = "",
    val revenue: Long = 0,
    val budget: Long = 0,
    val runtime: Int = 0,
    val vote: Double = 0.0,
    val voteCount: Int = 0,
    val language: String = "",
    val countries: List<String> = emptyList(),
    val collectionId: Int = 0,
    val collectionName: String = "",
    val collectionPoster: String? = null,
    val collectionBackdrop: String? = null,
) {
    val year: String get() = releaseDate.take(4)
    val profit: Long get() = if (budget > 0 && revenue > 0) revenue - budget else 0
    /** Gross over budget, as a multiple. Null without both figures. */
    val multiple: Double? get() = if (budget > 0 && revenue > 0) revenue.toDouble() / budget else null

    fun asItem() = MediaItem(
        id = id, type = MediaType.Movie, title = title, posterPath = poster,
        backdropPath = backdrop, voteAverage = vote, voteCount = voteCount,
        releaseDate = releaseDate, originalLanguage = language,
    )

    /**
     * An Indian production. Country decides when TMDB has it; the language is
     * only a fallback, because a Tamil-language film made in Sri Lanka is not
     * one and a Hindi film made in India always is.
     */
    val isIndian: Boolean
        get() = if (countries.isNotEmpty()) "IN" in countries else language in IndianLanguages
}

/** One collection's whole revenue history. */
@Immutable
data class FranchiseMoney(
    val id: Int,
    val name: String,
    val poster: String?,
    val backdrop: String?,
    val films: List<FilmMoney>,
) {
    val revenue: Long get() = films.sumOf { it.revenue }
    val reported: Int get() = films.count { it.revenue > 0 }
    /** How many of the released films TMDB actually has a figure for. */
    val coverage: Int get() = if (films.isEmpty()) 0 else reported * 100 / films.size
    val topFilm: FilmMoney? get() = films.filter { it.revenue > 0 }.maxByOrNull { it.revenue }
}

private val IndianLanguages = setOf("hi", "ta", "te", "ml", "kn", "bn", "mr", "pa", "gu", "ur", "or", "as")

class BoxOfficeRepository(
    private val tmdb: TmdbRepository,
    private val http: OkHttpClient,
) {
    private val pages = HashMap<Int, Pair<List<FilmMoney>, Int>>()
    private val collections = HashMap<Int, FranchiseMoney>()
    private val lock = Mutex()
    private var rate: Double = 0.0

    /**
     * One revenue-ranked page of twenty, with every amount the reported one.
     * Returns the rows and how many pages there are (ten at most — the chart is
     * an all-time top two hundred, not the whole of cinema).
     */
    suspend fun page(number: Int): Pair<List<FilmMoney>, Int> {
        lock.withLock { pages[number] }?.let { return it }
        val (candidates, total) = tmdb.discoverPage(
            MediaType.Movie,
            mapOf(
                "sort_by" to "revenue.desc",
                "primary_release_date.lte" to LocalDate.now().toString(),
                "vote_count.gte" to "50",
            ),
            number,
        )
        val rows = hydrate(candidates.map { it.id })
            .filter { it.revenue > 0 }
            .sortedWith(compareByDescending<FilmMoney> { it.revenue }.thenByDescending { it.voteCount })
        val result = rows to minOf(10, total.coerceAtLeast(1))
        // An empty page is not remembered: it is far more likely to be a
        // dropped connection than a chart that genuinely ran out.
        if (rows.isNotEmpty()) lock.withLock { pages[number] = result }
        return result
    }

    /**
     * Every released part of a collection, with its reported revenue, in
     * release order — the franchise timeline and the league are both built
     * from this.
     */
    suspend fun collection(id: Int): FranchiseMoney? {
        lock.withLock { collections[id] }?.let { return it }
        val info = tmdb.collectionInfo(id) ?: return null
        val today = LocalDate.now()
        val films = hydrate(info.parts.map { it.id })
            .filter { film ->
                val day = runCatching { LocalDate.parse(film.releaseDate) }.getOrNull()
                if (day != null) !day.isAfter(today) else film.revenue > 0
            }
            .sortedWith(compareBy<FilmMoney> { it.releaseDate.ifBlank { "9999" } }.thenBy { it.id })
        val money = FranchiseMoney(id, info.name, info.poster, info.backdrop, films)
        lock.withLock { collections[id] = money }
        return money
    }

    /**
     * The franchises behind a chart, ranked by their whole runs rather than by
     * the parts that happen to be in the top two hundred.
     */
    suspend fun league(chart: List<FilmMoney>, limit: Int = 20): List<FranchiseMoney> {
        val candidates = chart.filter { it.collectionId > 0 }
            .groupBy { it.collectionId }
            .entries
            .sortedByDescending { (_, films) -> films.sumOf { it.revenue } }
            .take(limit * 2)
            .map { it.key }
        val gate = Semaphore(5)
        return coroutineScope {
            candidates.map { id -> async { gate.withPermit { collection(id) } } }.awaitAll()
        }
            .filterNotNull()
            .filter { it.reported > 0 }
            .sortedWith(compareByDescending<FranchiseMoney> { it.revenue }.thenBy { it.name })
            .take(limit)
    }

    /**
     * Today's US dollar to rupee reference rate, once per process.
     *
     * TMDB reports revenue in dollars. An Indian film is shown in crore as well,
     * because that is the unit anyone who follows that box office thinks in —
     * converted, and marked approximate, so it never looks like India nett.
     */
    suspend fun usdToInr(): Double {
        if (rate > 0) return rate
        rate = runCatching {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url("https://api.frankfurter.dev/v2/rate/USD/INR").build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body.string()
                    Http.json.parseToJsonElement(body).jsonObject["rate"]?.jsonPrimitive?.doubleOrNull
                }
            }
        }.getOrNull()?.takeIf { it > 1 } ?: 90.0
        return rate
    }

    /** Detail records, five in flight; one film that fails is simply left out. */
    private suspend fun hydrate(ids: List<Int>): List<FilmMoney> {
        val gate = Semaphore(5)
        return coroutineScope {
            ids.distinct().map { id -> async { gate.withPermit { tmdb.filmMoney(id) } } }.awaitAll()
        }.filterNotNull()
    }

    fun clear() {
        pages.clear()
        collections.clear()
    }
}

object Money {

    /** "$2.92B", "$845.6M", or the whole figure in dollars. */
    fun usd(value: Long, compact: Boolean = true): String {
        if (value <= 0) return "Not reported"
        if (!compact) {
            return NumberFormat.getCurrencyInstance(ULocale.US).apply { maximumFractionDigits = 0 }
                .format(value)
        }
        val v = value.toDouble()
        return when {
            v >= 1e9 -> "$%.2fB".format(Locale.US, v / 1e9)
            v >= 1e6 -> "$%.1fM".format(Locale.US, v / 1e6)
            v >= 1e3 -> "$%.0fK".format(Locale.US, v / 1e3)
            else -> "$%,d".format(Locale.US, value)
        }
    }

    /** "≈₹1,234 Cr", grouped the Indian way (1,23,456), never mistaken for nett. */
    fun inr(usd: Long, rate: Double): String {
        if (usd <= 0) return "Not reported"
        val crore = usd * rate / 1e7
        val format = NumberFormat.getNumberInstance(ULocale("en_IN")).apply {
            maximumFractionDigits = if (crore >= 100) 0 else 1
        }
        return "≈₹${format.format(crore)} Cr"
    }

    /** "3.2×", or a dash without both figures. */
    fun multiple(value: Double?): String = value?.let { "%.1f×".format(Locale.US, it) } ?: "—"
}
