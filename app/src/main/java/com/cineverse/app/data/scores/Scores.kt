package com.cineverse.app.data.scores

import androidx.compose.runtime.Immutable
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.tmdb.LruCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/**
 * IMDb, the Tomatometer and Metacritic, from sources that cost nothing.
 *
 * Ported from the website's `js/scores.js`, including the parts it learned the
 * hard way:
 *
 *  - **IMDb** comes from **Cinemeta** (`v3-cinemeta.strem.io`), Stremio's public
 *    catalogue: one request per title, no key.
 *  - **Rotten Tomatoes and Metacritic** come from **Wikidata**, where they are
 *    stored as review scores with the reviewer named. Good for films, patchy for
 *    television.
 *  - **An OMDb key fills the gaps**, television especially, and is the only free
 *    route to the AUDIENCE meter beside the critics' one.
 *  - Cinemeta also publishes per-episode numbers and they are **not** IMDb's —
 *    Ozymandias comes back 8.4 where IMDb says 9.9 — so episode ratings
 *    everywhere in CineVerse are TMDB's instead.
 */
@Immutable
data class Scores(
    val imdb: Double = 0.0,
    val rt: Int = 0,
    val rtAverage: Double = 0.0,
    val rtAudience: Int = 0,
    val metacritic: Int = 0,
) {
    val any: Boolean get() = imdb > 0 || rt > 0 || rtAudience > 0 || metacritic > 0

    companion object { val Empty = Scores() }
}

/** What an OMDb answer says about the key that asked for it. */
enum class OmdbVerdict { Ok, Quota, Invalid, Error }

class ScoresRepository(
    private val client: OkHttpClient,
    private val omdbKey: () -> String,
    private val onOmdbTrouble: (OmdbVerdict) -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cache = LruCache<String, Pair<Long, Scores>>(400)
    private val gate = Semaphore(4)
    private var troubleSaid: String = ""

    private val ttl = 24L * 60 * 60 * 1000

    /** The cached answer, for a screen that wants to paint before it fetches. */
    fun cached(imdbId: String, type: MediaType): Scores? {
        val held = cache["${imdbId}_${type.wire}"] ?: return null
        return if (System.currentTimeMillis() - held.first < ttl) held.second else null
    }

    suspend fun of(imdbId: String, type: MediaType): Scores {
        if (!imdbId.matches(Regex("^tt\\d+$"))) return Scores.Empty
        cached(imdbId, type)?.let { return it }
        return gate.withPermit {
            cached(imdbId, type) ?: fetch(imdbId, type).also {
                cache.put("${imdbId}_${type.wire}", System.currentTimeMillis() to it)
            }
        }
    }

    private suspend fun fetch(imdbId: String, type: MediaType): Scores = coroutineScope {
        val key = omdbKey()
        val meta = async { runCatching { fromCinemeta(imdbId, type) }.getOrDefault(Scores.Empty) }
        val wiki = async { runCatching { fromWikidata(imdbId) }.getOrDefault(Scores.Empty) }
        val omdb = async {
            if (key.isBlank()) null else runCatching { fromOmdb(imdbId, key) }.getOrNull()
        }
        // OMDb first where it answered, then the keyless pair behind it — so a
        // key fills television in without changing anything else.
        merge(omdb.await() ?: Scores.Empty, meta.await(), wiki.await())
    }

    private suspend fun get(url: String): String? = withContext(io) {
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        }.getOrNull()
    }

    private suspend fun fromCinemeta(imdbId: String, type: MediaType): Scores {
        val kind = if (type == MediaType.Tv) "series" else "movie"
        val body = get("https://v3-cinemeta.strem.io/meta/$kind/$imdbId.json") ?: return Scores.Empty
        val meta = json.parseToJsonElement(body).jsonObject["meta"]?.jsonObject ?: return Scores.Empty
        return Scores(imdb = rating(meta["imdbRating"]?.jsonPrimitive?.contentOrNull))
    }

    private suspend fun fromWikidata(imdbId: String): Scores {
        val query = "SELECT ?byLabel ?score WHERE { ?item wdt:P345 \"$imdbId\". " +
            "?item p:P444 ?statement. ?statement ps:P444 ?score. ?statement pq:P447 ?by. " +
            "SERVICE wikibase:label { bd:serviceParam wikibase:language \"en\". } }"
        val url = "https://query.wikidata.org/sparql?format=json&query=" +
            URLEncoder.encode(query, "UTF-8")
        val body = get(url) ?: return Scores.Empty
        val bindings = runCatching {
            json.parseToJsonElement(body).jsonObject["results"]?.jsonObject
                ?.get("bindings")?.jsonArray.orEmpty()
        }.getOrDefault(emptyList())
        return parseWikidata(bindings.map { it.jsonObject })
    }

    private suspend fun fromOmdb(imdbId: String, key: String): Scores {
        val url = "https://www.omdbapi.com/?i=$imdbId&tomatoes=true&apikey=" +
            URLEncoder.encode(key, "UTF-8")
        val body = get(url) ?: run { raise(OmdbVerdict.Error, key); return Scores.Empty }
        val data = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: run { raise(OmdbVerdict.Error, key); return Scores.Empty }
        val verdict = verdictOf(data)
        if (verdict != OmdbVerdict.Ok) { raise(verdict, key); return Scores.Empty }
        troubleSaid = ""
        return parseOmdb(data)
    }

    /**
     * A key that stops working is said once, not on every title. A key is pasted
     * and forgotten, so the app has to be the one that notices.
     */
    private fun raise(verdict: OmdbVerdict, key: String) {
        val signature = "$key:$verdict"
        if (troubleSaid == signature) return
        troubleSaid = signature
        onOmdbTrouble(verdict)
    }

    companion object {

        private val kotlinx.serialization.json.JsonPrimitive.contentOrNull: String?
            get() = runCatching { content }.getOrNull()

        /** "8.8" | 8.8 | "N/A" -> 8.8 or 0. Out of range is no rating. */
        fun rating(value: String?): Double {
            val number = value?.trim()?.toDoubleOrNull() ?: return 0.0
            return if (number > 0 && number <= 10) Math.round(number * 10) / 10.0 else 0.0
        }

        /**
         * Wikidata review scores. Rotten Tomatoes stores both a critics
         * percentage ("87%") and a ten-point average ("8.1/10"); the percentage
         * is the Tomatometer everyone means.
         */
        fun parseWikidata(rows: List<JsonObject>): Scores {
            var rt = 0; var average = 0.0; var metacritic = 0
            val percent = Regex("^(\\d{1,3})\\s*%$")
            val outOfTen = Regex("^([\\d.]+)\\s*/\\s*10$")
            val outOfHundred = Regex("^(\\d{1,3})\\s*/\\s*100$")
            for (row in rows) {
                val by = row["byLabel"]?.jsonObject?.get("value")?.jsonPrimitive?.contentOrNull
                    ?.lowercase().orEmpty()
                val raw = row["score"]?.jsonObject?.get("value")?.jsonPrimitive?.contentOrNull
                    ?.trim().orEmpty()
                when {
                    by.contains("rotten") -> {
                        percent.find(raw)?.let { rt = maxOf(rt, it.groupValues[1].toInt().coerceAtMost(100)) }
                            ?: outOfTen.find(raw)?.let { average = maxOf(average, rating(it.groupValues[1])) }
                    }
                    by.contains("metacritic") -> {
                        val match = outOfHundred.find(raw) ?: percent.find(raw)
                        match?.let { metacritic = maxOf(metacritic, it.groupValues[1].toInt().coerceAtMost(100)) }
                    }
                }
            }
            return Scores(rt = rt, rtAverage = average, metacritic = metacritic)
        }

        /**
         * OMDb's payload. `tomatoes=true` can also carry the audience meter,
         * which is the only free source for the other half of the Tomatometer —
         * though many keys answer "N/A", and "N/A" is not a score.
         */
        fun parseOmdb(data: JsonObject): Scores {
            fun field(name: String) = data[name]?.jsonPrimitive?.contentOrNull?.trim()
            var imdb = rating(field("imdbRating"))
            var rt = 0
            var audience = 0
            var metacritic = 0
            var average = 0.0

            data["Ratings"]?.let { element ->
                runCatching {
                    for (entry in element.jsonArray) {
                        val row = entry.jsonObject
                        val source = row["Source"]?.jsonPrimitive?.contentOrNull?.lowercase().orEmpty()
                        val value = row["Value"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                        when {
                            source.contains("rotten") ->
                                Regex("^(\\d{1,3})%$").find(value)?.let { rt = it.groupValues[1].toInt() }
                            source.contains("metacritic") ->
                                Regex("^(\\d{1,3})/100$").find(value)?.let { metacritic = it.groupValues[1].toInt() }
                            source.contains("internet movie") ->
                                if (imdb <= 0) imdb = rating(value.substringBefore('/'))
                        }
                    }
                }
            }
            if (metacritic == 0) field("Metascore")?.toIntOrNull()?.let { metacritic = it }
            if (rt == 0) field("tomatoMeter")?.trimEnd('%')?.toIntOrNull()?.let { rt = it }
            field("tomatoUserMeter")?.trimEnd('%')?.toIntOrNull()?.let { audience = it }
            if (average <= 0) field("tomatoRating")?.let { average = rating(it) }

            return Scores(
                imdb = imdb,
                rt = rt.coerceIn(0, 100),
                rtAverage = average,
                rtAudience = audience.coerceIn(0, 100),
                metacritic = metacritic.coerceIn(0, 100),
            )
        }

        /** A real number beats a zero, in the order the sources are trusted. */
        fun merge(vararg sources: Scores): Scores = Scores(
            imdb = sources.firstNotNullOfOrNull { it.imdb.takeIf { v -> v > 0 } } ?: 0.0,
            rt = sources.firstNotNullOfOrNull { it.rt.takeIf { v -> v > 0 } } ?: 0,
            rtAverage = sources.firstNotNullOfOrNull { it.rtAverage.takeIf { v -> v > 0 } } ?: 0.0,
            rtAudience = sources.firstNotNullOfOrNull { it.rtAudience.takeIf { v -> v > 0 } } ?: 0,
            metacritic = sources.firstNotNullOfOrNull { it.metacritic.takeIf { v -> v > 0 } } ?: 0,
        )

        /**
         * OMDb answers 200 with `Response: "False"` for a key it will not serve,
         * so the message is the only thing that tells a spent quota from a dead
         * key — and "Incorrect IMDb ID" says nothing about the key at all.
         */
        fun verdictOf(data: JsonObject): OmdbVerdict {
            val response = data["Response"]?.jsonPrimitive?.contentOrNull
            if (response != "False") return OmdbVerdict.Ok
            val message = data["Error"]?.jsonPrimitive?.contentOrNull?.lowercase().orEmpty()
            return when {
                message.contains("limit") -> OmdbVerdict.Quota
                message.contains("key") -> OmdbVerdict.Invalid
                else -> OmdbVerdict.Ok
            }
        }

        fun troubleMessage(verdict: OmdbVerdict): String = when (verdict) {
            OmdbVerdict.Quota -> "Your OMDb key has used up today's requests. Ratings fall back to the free sources until it resets."
            OmdbVerdict.Invalid -> "Your OMDb key was refused. Check it in Settings, or clear it to use the free sources."
            else -> "OMDb could not be reached. Ratings fall back to the free sources."
        }
    }
}
