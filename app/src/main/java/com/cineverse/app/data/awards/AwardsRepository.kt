package com.cineverse.app.data.awards

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/**
 * What a title has won, from Wikidata.
 *
 * TMDB has no awards feed at all, which is why the website went to Wikidata for
 * them and why this does the same: the same SPARQL query against the same
 * public endpoint, keyed on the IMDb id, so the phone and the laptop report the
 * same trophies.
 *
 * Two rules the website set that are worth keeping, because they are the
 * difference between a credible panel and a decorative one:
 *
 *  - **Nothing is invented.** No trophy is drawn that Wikidata did not return,
 *    and no imitation award logo is ever shown. A win with no artwork is listed
 *    as text.
 *  - **Only the ones people have heard of.** Wikidata records hundreds of minor
 *    honours; a list that leads with "Best Sound Editing, Golden Trailer
 *    Awards" buries the Oscar underneath it.
 */
@Immutable
data class Award(
    val name: String,
    val year: String,
    val won: Boolean,
    val programme: String,
)

@Immutable
data class Awards(
    val wins: List<Award> = emptyList(),
    val nominations: List<Award> = emptyList(),
) {
    val any: Boolean get() = wins.isNotEmpty() || nominations.isNotEmpty()
    val total: Int get() = wins.size + nominations.size
}

class AwardsRepository(
    private val client: OkHttpClient,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<String, Awards>()
    private val lock = Mutex()

    suspend fun of(imdbId: String): Awards {
        if (!imdbId.matches(Regex("^tt\\d+$"))) return Awards()
        lock.withLock { cache[imdbId] }?.let { return it }
        val result = withContext(io) { runCatching { fetch(imdbId) }.getOrDefault(Awards()) }
        lock.withLock { cache[imdbId] = result }
        return result
    }

    fun cached(imdbId: String): Awards? = cache[imdbId]

    private fun fetch(imdbId: String): Awards {
        val query = QUERY.replace("%IMDB%", imdbId)
        val url = "https://query.wikidata.org/sparql?format=json&query=" +
            URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/sparql-results+json")
            // Wikidata asks every client to identify itself and throttles the
            // ones that do not. This is the polite thing and also the thing
            // that stops the endpoint returning 403 under load.
            .header("User-Agent", "CineVerse/1.0 (https://cineverse.pages.dev)")
            .build()

        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return Awards()
            response.body?.string()
        } ?: return Awards()

        val rows = json.parseToJsonElement(body)
            .jsonObject["results"]?.jsonObject
            ?.get("bindings")?.jsonArray
            ?: return Awards()

        val seen = mutableSetOf<String>()
        val wins = mutableListOf<Award>()
        val nominations = mutableListOf<Award>()

        for (row in rows) {
            val fields = row.jsonObject
            fun value(key: String) = fields[key]?.jsonObject
                ?.get("value")?.jsonPrimitive?.content.orEmpty()

            val label = value("honorLabel").ifBlank { continue }
            if (!MAJOR.containsMatchIn(label)) continue
            val kind = value("kind")
            val year = value("date").take(4)

            // Wikidata returns the same honour several times over, once per
            // optional branch that matched. Deduplicated on name AND outcome,
            // so a nomination and a win for the same award both survive.
            val key = "$kind|$label|$year"
            if (!seen.add(key)) continue

            val award = Award(
                name = label,
                year = year,
                won = kind == "win",
                programme = PROGRAMMES.firstOrNull { it.second.containsMatchIn(label) }
                    ?.first
                    ?: label.substringBefore(" for ").take(40),
            )
            if (award.won) wins += award else nominations += award
        }

        return Awards(
            wins = wins.sortedByDescending { it.year }.take(24),
            // A win and a nomination for the same award is a win; listing it
            // under both is how a panel ends up claiming more than happened.
            nominations = nominations
                .filterNot { nomination -> wins.any { it.name == nomination.name } }
                .sortedByDescending { it.year }
                .take(24),
        )
    }

    private companion object {
        /** The programmes anybody has heard of, in the website's own order. */
        val PROGRAMMES = listOf(
            "Academy Awards" to Regex("academy award|oscar", RegexOption.IGNORE_CASE),
            "Golden Globes" to Regex("golden globe", RegexOption.IGNORE_CASE),
            "BAFTA" to Regex("bafta|british academy film", RegexOption.IGNORE_CASE),
            "Emmy Awards" to Regex("emmy", RegexOption.IGNORE_CASE),
            "Festival de Cannes" to Regex("cannes|palme d.or", RegexOption.IGNORE_CASE),
            "Venice" to Regex("venice film festival", RegexOption.IGNORE_CASE),
            "Sundance" to Regex("sundance", RegexOption.IGNORE_CASE),
            "Berlinale" to Regex("berlin international|berlinale|golden bear|silver bear", RegexOption.IGNORE_CASE),
            "Filmfare" to Regex("filmfare", RegexOption.IGNORE_CASE),
            "IIFA" to Regex("iifa", RegexOption.IGNORE_CASE),
            "National Film Awards" to Regex("national film award", RegexOption.IGNORE_CASE),
            "SAG Awards" to Regex("screen actors guild", RegexOption.IGNORE_CASE),
            "Critics Choice" to Regex("critics.? choice", RegexOption.IGNORE_CASE),
            "Independent Spirit" to Regex("independent spirit", RegexOption.IGNORE_CASE),
            "Grammy Awards" to Regex("grammy", RegexOption.IGNORE_CASE),
            "Saturn Awards" to Regex("saturn award", RegexOption.IGNORE_CASE),
            "Gotham Awards" to Regex("gotham", RegexOption.IGNORE_CASE),
            "César Awards" to Regex("c[eé]sar", RegexOption.IGNORE_CASE),
            "Goya Awards" to Regex("goya", RegexOption.IGNORE_CASE),
            "NAACP Image Awards" to Regex("naacp image", RegexOption.IGNORE_CASE),
        )

        val MAJOR = Regex(
            "academy award|oscar|golden globe|bafta|emmy|cannes|venice|sundance|berlin|" +
                "critics.? choice|screen actors guild|grammy|palme d'or|tony award|filmfare|" +
                "national film award|iifa|independent spirit|gotham|c[eé]sar|goya|" +
                "saturn award|naacp image",
            RegexOption.IGNORE_CASE,
        )

        /** The website's query, unchanged except for the artwork branches. */
        val QUERY = """
            PREFIX wdt: <http://www.wikidata.org/prop/direct/>
            PREFIX p: <http://www.wikidata.org/prop/>
            PREFIX ps: <http://www.wikidata.org/prop/statement/>
            PREFIX pq: <http://www.wikidata.org/prop/qualifier/>
            PREFIX wikibase: <http://wikiba.se/ontology#>
            PREFIX bd: <http://www.bigdata.com/rdf#>
            SELECT DISTINCT ?kind ?honorLabel ?date WHERE {
              ?work wdt:P345 "%IMDB%".
              { ?work p:P166 ?statement. ?statement ps:P166 ?honor. BIND("win" AS ?kind) }
              UNION { ?work p:P1411 ?statement. ?statement ps:P1411 ?honor. BIND("nomination" AS ?kind) }
              OPTIONAL { ?statement pq:P585 ?statementDate. }
              OPTIONAL { ?honor wdt:P585 ?honorDate. }
              BIND(COALESCE(?statementDate, ?honorDate) AS ?date)
              SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
            } LIMIT 160
        """.trimIndent()
    }
}
