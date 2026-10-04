package com.cineverse.app.data.wiki

import android.content.Context
import androidx.compose.runtime.Immutable
import com.cineverse.app.core.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/** What a title was adapted from. */
@Immutable
@Serializable
data class Source(val title: String, val kind: String, val by: String = "") {
    /** "the novel Forrest Gump by Winston Groom", "the true story of the Apollo 13 mission". */
    val sentence: String
        get() = when (kind) {
            "true story" -> "the true story of $title"
            "" -> title + (by.takeIf { it.isNotBlank() }?.let { " by $it" } ?: "")
            else -> "the $kind $title" + (by.takeIf { it.isNotBlank() }?.let { " by $it" } ?: "")
        }
}

/** Box office as Wikidata has it, in US dollars. */
@Immutable
@Serializable
data class Takings(
    val worldwide: Long = 0,
    val domestic: Long = 0,
    /** All-time rank of the worldwide figure, when recorded. */
    val rank: Int = 0,
    /** Rarely recorded: a gross over a span of a week or less. */
    val opening: Long = 0,
) {
    val any: Boolean get() = worldwide > 0 || domestic > 0 || opening > 0
}

@Immutable
@Serializable
data class WikiFacts(val basedOn: List<Source> = emptyList(), val takings: Takings = Takings())

/**
 * "Based on" and box office, from Wikidata: what a title was adapted from
 * (a novel, a comic, a play, a game, a true story) with its author, and the
 * box-office statements - worldwide, US, the all-time ranking, and an opening
 * weekend in the few cases Wikidata records one. Kept for a month.
 */
class WikiFactsRepository(context: Context, private val http: OkHttpClient) {

    private val prefs = context.getSharedPreferences("wiki_facts", Context.MODE_PRIVATE)

    suspend fun of(imdbId: String): WikiFacts {
        if (!imdbId.matches(Regex("^tt\\d+$"))) return WikiFacts()
        prefs.getString(imdbId, null)?.let { raw ->
            val at = raw.substringBefore('\n').toLongOrNull() ?: 0
            if (System.currentTimeMillis() - at < 30 * 86_400_000L) {
                runCatching { return Http.json.decodeFromString<WikiFacts>(raw.substringAfter('\n')) }
            }
        }
        val basedOn = query(
            "SELECT ?srcLabel ?typeLabel ?authorLabel WHERE { ?item wdt:P345 \"$imdbId\". ?item wdt:P144 ?src. " +
                "OPTIONAL { ?src wdt:P31 ?type. } OPTIONAL { ?src wdt:P50 ?author. } " +
                "SERVICE wikibase:label { bd:serviceParam wikibase:language \"en\". } } LIMIT 20"
        )?.let { rows ->
            rows.groupBy { it["srcLabel"].orEmpty() }.filterKeys { it.isNotBlank() && !it.matches(Regex("Q\\d+")) }
                .map { (title, same) ->
                    val types = same.mapNotNull { it["typeLabel"] }.map { it.lowercase() }
                    Source(title, kindOf(types), same.firstNotNullOfOrNull { it["authorLabel"]?.takeIf { a -> !a.matches(Regex("Q\\d+")) } }.orEmpty())
                }.take(3)
        }
        val takings = query(
            "SELECT ?st ?amount ?placeLabel ?rank ?start ?end ?duration WHERE { ?item wdt:P345 \"$imdbId\". ?item p:P2142 ?st. " +
                "?st ps:P2142 ?amount. ?st wikibase:rank ?r. FILTER(?r != wikibase:DeprecatedRank) " +
                "OPTIONAL { ?st pq:P3005 ?place. } OPTIONAL { ?st pq:P1352 ?rank. } OPTIONAL { ?st pq:P580 ?start. } " +
                "OPTIONAL { ?st pq:P582 ?end. } OPTIONAL { ?st pq:P2047 ?duration. } " +
                "SERVICE wikibase:label { bd:serviceParam wikibase:language \"en\". } } LIMIT 40"
        )?.let { rows ->
            var worldwide = 0L
            var domestic = 0L
            var rank = 0
            var opening = 0L
            for (row in rows) {
                val amount = row["amount"]?.toDoubleOrNull()?.toLong() ?: continue
                val place = row["placeLabel"].orEmpty().lowercase()
                val start = row["start"]?.take(10)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
                val end = row["end"]?.take(10)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
                val days = if (start != null && end != null) java.time.temporal.ChronoUnit.DAYS.between(start, end) else null
                val duration = row["duration"]?.toDoubleOrNull()
                val short = (days != null && days in 0..7) || (duration != null && duration <= 7)
                when {
                    short -> opening = maxOf(opening, amount)
                    place.contains("worldwide") || place.isBlank() -> {
                        if (amount > worldwide) { worldwide = amount; rank = row["rank"]?.toDoubleOrNull()?.toInt() ?: rank }
                    }
                    place.contains("united states") || place.contains("north america") -> domestic = maxOf(domestic, amount)
                }
            }
            Takings(worldwide, domestic, rank, opening)
        }
        val facts = WikiFacts(basedOn.orEmpty(), takings ?: Takings())
        if (basedOn != null || takings != null) {
            prefs.edit().putString(imdbId, "${System.currentTimeMillis()}\n" + Http.json.encodeToString(facts)).apply()
        }
        return facts
    }

    /** "novel", "comic", "play" ... from what Wikidata says the source is. */
    private fun kindOf(types: List<String>): String = when {
        types.any { "novel" in it && "graphic" !in it } -> "novel"
        types.any { "graphic novel" in it } -> "graphic novel"
        types.any { "manga" in it } -> "manga"
        types.any { "comic" in it } -> "comic"
        types.any { "short story" in it } -> "short story"
        types.any { "video game" in it } -> "video game"
        types.any { "musical" in it } -> "musical"
        types.any { "play" in it || "stage" in it } -> "play"
        types.any { "television series" in it } -> "series"
        types.any { "film" in it } -> "film"
        types.any { "article" in it } -> "article"
        types.any { "book" in it || "literary work" in it || "memoir" in it || "biography" in it } -> "book"
        types.any { "human" in it || "event" in it || "war" in it || "battle" in it || "mission" in it || "incident" in it || "disaster" in it || "crime" in it } -> "true story"
        else -> ""
    }

    private suspend fun query(sparql: String): List<Map<String, String>>? = withTimeoutOrNull(25_000) {
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("https://query.wikidata.org/sparql?format=json&query=" + URLEncoder.encode(sparql, "UTF-8"))
                    .header("Accept", "application/sparql-results+json")
                    .header("User-Agent", "CineVerse/1.0 (https://cineverse.pages.dev)")
                    .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    Http.json.parseToJsonElement(response.body.string()).jsonObject["results"]?.jsonObject
                        ?.get("bindings")?.jsonArray.orEmpty().map { row ->
                            row.jsonObject.mapValues { (_, v) -> v.jsonObject["value"]?.jsonPrimitive?.contentOrNull.orEmpty() }
                        }
                }
            }.getOrNull()
        }
    }
}
