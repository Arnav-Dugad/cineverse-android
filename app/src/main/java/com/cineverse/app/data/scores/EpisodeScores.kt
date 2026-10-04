package com.cineverse.app.data.scores

import android.content.Context
import androidx.compose.runtime.Immutable
import com.cineverse.app.core.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/** A season's episode scores, and whose they are. */
@Immutable
data class SeasonScores(
    /** Episode number to score out of ten. */
    val byEpisode: Map<Int, Double> = emptyMap(),
    /** "IMDb" with an OMDb key, "TVmaze" without one. */
    val source: String = "",
) {
    val isEmpty: Boolean get() = byEpisode.isEmpty()
}

/**
 * Each episode's score, for the season curve and the badge on every row.
 *
 * IMDb's, through OMDb, when you have put an OMDb key in Settings - OMDb is
 * the one place IMDb's episode ratings can be read. Without a key, TVmaze's
 * own viewer ratings, labelled as TVmaze's: a curve is still worth seeing,
 * but it must not claim to be IMDb's.
 *
 * Kept on the device for three days, which is about as fast as a season's
 * scores settle.
 */
class EpisodeScores(context: Context, private val http: OkHttpClient, private val omdbKey: () -> String) {

    private val prefs = context.getSharedPreferences("episode_scores", Context.MODE_PRIVATE)
    private val memory = HashMap<String, SeasonScores>()

    suspend fun season(imdbId: String, showName: String, season: Int): SeasonScores {
        if (season <= 0) return SeasonScores()
        val key = omdbKey().trim()
        val cacheKey = "${imdbId.ifBlank { showName }}:$season:${if (key.isBlank()) "maze" else "omdb"}"
        memory[cacheKey]?.let { return it }
        prefs.getString(cacheKey, null)?.let { raw ->
            val at = raw.substringBefore('|').toLongOrNull() ?: 0
            if (System.currentTimeMillis() - at < 3 * 86_400_000L) {
                val parsed = decode(raw.substringAfter('|'))
                memory[cacheKey] = parsed
                return parsed
            }
        }
        val fetched = withContext(Dispatchers.IO) {
            (if (key.isNotBlank() && imdbId.isNotBlank()) runCatching { omdb(imdbId, season, key) }.getOrNull() else null)
                ?.takeIf { !it.isEmpty }
                ?: runCatching { tvmaze(imdbId, showName, season) }.getOrNull()
        } ?: return SeasonScores()
        memory[cacheKey] = fetched
        prefs.edit().putString(cacheKey, "${System.currentTimeMillis()}|${encode(fetched)}").apply()
        return fetched
    }

    private fun get(url: String): String? =
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) null else response.body.string()
        }

    private fun omdb(imdbId: String, season: Int, key: String): SeasonScores? {
        val body = get("https://www.omdbapi.com/?i=$imdbId&Season=$season&apikey=" + URLEncoder.encode(key, "UTF-8")) ?: return null
        val root = Http.json.parseToJsonElement(body).jsonObject
        val episodes = root["Episodes"] as? JsonArray ?: return null
        val scores = episodes.mapNotNull { element ->
            val episode = element.jsonObject
            val number = episode["Episode"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@mapNotNull null
            val rating = episode["imdbRating"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: return@mapNotNull null
            number to rating
        }.toMap()
        return SeasonScores(scores, "IMDb")
    }

    private fun tvmaze(imdbId: String, showName: String, season: Int): SeasonScores? {
        val show = (if (imdbId.isNotBlank()) get("https://api.tvmaze.com/lookup/shows?imdb=$imdbId") else null)
            ?: get("https://api.tvmaze.com/singlesearch/shows?q=" + URLEncoder.encode(showName, "UTF-8"))
            ?: return null
        val id = Http.json.parseToJsonElement(show).jsonObject["id"]?.jsonPrimitive?.intOrNull ?: return null
        val body = get("https://api.tvmaze.com/shows/$id/episodes") ?: return null
        val scores = Http.json.parseToJsonElement(body).jsonArray.mapNotNull { element ->
            val episode = element.jsonObject
            if (episode["season"]?.jsonPrimitive?.intOrNull != season) return@mapNotNull null
            val number = episode["number"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val rating = episode["rating"]?.jsonObject?.get("average")?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            number to rating
        }.toMap()
        return SeasonScores(scores, "TVmaze")
    }

    private fun encode(scores: SeasonScores) =
        scores.source + ";" + scores.byEpisode.entries.joinToString(",") { "${it.key}=${it.value}" }

    private fun decode(raw: String): SeasonScores {
        val source = raw.substringBefore(';')
        val pairs = raw.substringAfter(';').split(',').mapNotNull { part ->
            val number = part.substringBefore('=').toIntOrNull() ?: return@mapNotNull null
            val rating = part.substringAfter('=').toDoubleOrNull() ?: return@mapNotNull null
            number to rating
        }.toMap()
        return SeasonScores(pairs, source)
    }
}
