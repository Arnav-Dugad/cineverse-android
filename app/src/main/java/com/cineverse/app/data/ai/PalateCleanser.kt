package com.cineverse.app.data.ai

import android.content.Context
import androidx.compose.runtime.Immutable
import com.cineverse.app.AppContainer
import com.cineverse.app.core.net.Http
import com.cineverse.app.data.diary.Diary
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A run of heavy viewing noticed, and something lighter offered. */
@Immutable
data class Cleanser(val id: String, val line: String, val picks: List<MediaItem>)

/**
 * Binge fatigue: when the last three things you watched (in the last four
 * days) were all heavy - drama, crime, war, horror, thrillers, with nothing
 * lighter mixed in - Gemini says so in a line ("Three heavy dramas in a
 * row...") and offers a few lighter titles you have not seen, your own
 * watchlist first. "Not now" puts it away until the run changes.
 */
class PalateCleanser(private val app: AppContainer) {

    private val prefs = app.context.getSharedPreferences("palate", Context.MODE_PRIVATE)

    private val heavy = setOf(18, 10752, 80, 27, 53, 9648, 36, 10768)
    private val light = setOf(35, 16, 10751, 10402, 10749, 10762)

    fun dismiss(id: String) = prefs.edit().putString("dismissed", id).apply()

    suspend fun check(): Cleanser? {
        val library = app.privacy.library()
        val shows = app.privacy.shows()
        val since = System.currentTimeMillis() - 4 * 86_400_000L
        val recent = Diary.entries(library, shows).filter { it.at >= since }
            .sortedByDescending { it.at }.distinctBy { it.item.key }.take(3)
        if (recent.size < 3) return null
        val region = app.settings.settings.value.region
        fun genres(item: MediaItem): List<Int> = item.genreIds.ifEmpty {
            library.watched[item.key]?.genres ?: library.saved[item.key]?.genres
                ?: app.tmdb.cachedDetail(item.id, item.type, region)?.genres?.map { it.id } ?: emptyList()
        }
        val run = recent.map { it.item to genres(it.item) }
        if (run.any { (_, g) -> g.isEmpty() || g.none { it in heavy } || g.any { it in light } }) return null
        val id = run.joinToString(",") { it.first.key }
        if (prefs.getString("dismissed", null) == id) return null

        val watchlist = library.saved.values.filterNot { library.isWatched(it.key) }
            .filter { s -> s.genres.any { it in light } && s.genres.none { it == 27 } }
            .sortedByDescending { it.addedAt }.take(20)
        val viewer = runCatching { app.persona.brief() }.getOrDefault("")
        val prompt = buildString {
            if (viewer.isNotBlank()) { appendLine(viewer); appendLine() }
            appendLine("The viewer's last three titles, in a row: ${run.joinToString("; ") { it.first.title }}. All heavy.")
            appendLine("Write one warm, light line (under 22 words) noticing the run and offering a palate cleanser - like a friend,")
            appendLine("not a nurse; no lecture. Then pick 5 lighter titles they have NOT watched: comedy, animation, feel-good,")
            appendLine("comfort viewing, short if possible - from their watchlist first when any fit:")
            watchlist.forEach { appendLine("- ${it.title} (${it.year})") }
            appendLine("Reply with JSON only: {\"line\": string, \"picks\": [{\"title\": string, \"year\": integer, \"type\": \"movie\" or \"tv\"}]}")
        }
        val raw = app.gemini.json(prompt, timeoutMs = 20_000) ?: return null
        val obj = runCatching { Http.json.parseToJsonElement(Gemini.extractJson(raw) ?: "{}").jsonObject }.getOrNull() ?: return null
        val line = obj["line"]?.jsonPrimitive?.contentOrNull?.let(Gemini::plain)?.trim().orEmpty()
        if (line.isBlank()) return null
        val wanted = (obj["picks"] as? JsonArray).orEmpty().mapNotNull { element ->
            val row = element.jsonObject
            Triple(row["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null, row["year"]?.jsonPrimitive?.intOrNull, row["type"]?.jsonPrimitive?.contentOrNull)
        }
        val picks = coroutineScope {
            wanted.map { (title, year, type) ->
                async {
                    val media = when (type) { "tv" -> MediaType.Tv; "movie" -> MediaType.Movie; else -> null }
                    library.saved.values.firstOrNull { it.title.equals(title, true) }?.asItem()
                        ?: runCatching { app.tmdb.searchPage(title, 1, false).items }.getOrDefault(emptyList())
                            .filter { (media == null || it.type == media) && it.hasArt }
                            .firstOrNull { year == null || it.year.toIntOrNull()?.let { y -> kotlin.math.abs(y - year) <= 1 } != false }
                }
            }.awaitAll()
        }.filterNotNull().distinctBy { it.key }.filterNot { library.isWatched(it.key) }.take(5)
        if (picks.isEmpty()) return null
        return Cleanser(id, line, picks)
    }
}
