package com.cineverse.app.data.ai

import androidx.compose.runtime.Immutable
import com.cineverse.app.AppContainer
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What you are in the mood for, and the genres that answer it. */
enum class Mood(val label: String, val line: String, val genres: Set<Int>) {
    Funny("Light and funny", "something light", setOf(35, 10751, 16, 10402)),
    Tense("Edge of my seat", "something tense", setOf(53, 28, 80, 10759)),
    Moving("Something to feel", "something moving", setOf(18, 10749)),
    Mind("Mind-bending", "something mind-bending", setOf(878, 9648, 10765)),
    Scary("Properly scary", "something scary", setOf(27)),
    Epic("Big and epic", "something epic", setOf(12, 14, 36, 10752, 37)),
}

/** How long you have. [minutes] null means as long as it takes. */
enum class TimeBox(val label: String, val minutes: Int?) {
    Short("Under 90 minutes", 95),
    Evening("About two hours", 140),
    Night("All night", null),
}

/** Tonight's pick, and why. */
@Immutable
data class TonightPick(
    val item: MediaItem,
    val runtime: Int,
    val why: String,
    val byGemini: Boolean,
)

/**
 * "Pick tonight": one film from your own list for the mood you are in and
 * the time you have.
 *
 * The choosing is done here, in plain arithmetic, so it works with Gemini off:
 * the mood's genres, a runtime that fits, a good score, and a nudge for things
 * that have waited longest. Gemini, when it is on, is given the best few and
 * your taste, picks one, and says why in a sentence; when it is off, the app
 * says why itself.
 */
class Tonight(private val app: AppContainer) {

    /** Tonight's film, skipping anything in [exclude]; null when the list has nothing that fits. */
    suspend fun pick(mood: Mood, time: TimeBox, exclude: Set<String> = emptySet()): TonightPick? = coroutineScope {
        val library = app.privacy.library()
        val region = app.settings.settings.value.region
        val saved = library.saved.values
            .filter { it.type == MediaType.Movie && !library.isWatched(it.key) && it.key !in exclude }
        if (saved.isEmpty()) return@coroutineScope null

        // Runtimes the list does not carry are read from TMDB, a few at a time,
        // for the titles the mood favours anyway.
        val gate = Semaphore(4)
        val rough = saved.sortedByDescending { item -> item.genres.count { it in mood.genres } * 10 + item.rating }.take(24)
        val runtimes = rough.map { item ->
            async {
                item.key to (item.runtime.takeIf { it > 0 } ?: gate.withPermit {
                    runCatching { app.tmdb.detail(item.tmdbId, MediaType.Movie, region).runtime }.getOrDefault(0)
                })
            }
        }.awaitAll().toMap()

        val now = System.currentTimeMillis()
        val scored = rough.mapNotNull { item ->
            val runtime = runtimes[item.key] ?: 0
            val fits = time.minutes == null || (runtime in 1..time.minutes)
            if (!fits) return@mapNotNull null
            val moodHits = item.genres.count { it in mood.genres }
            val waitedDays = ((now - item.addedAt) / 86_400_000.0).coerceIn(0.0, 1500.0)
            val score = moodHits * 3.0 + item.rating * 0.6 + kotlin.math.ln(1 + waitedDays) * 0.35 -
                (if (moodHits == 0) 4.0 else 0.0)
            Triple(item, runtime, score)
        }.sortedByDescending { it.third }
        val best = scored.take(6).takeIf { it.isNotEmpty() } ?: return@coroutineScope null

        // Gemini chooses among the best few, if it is there.
        val loved = library.ratings.filterValues { it >= 8 }.keys
            .mapNotNull { library.watched[it]?.title ?: library.saved[it]?.title }.take(10)
        val viewer = runCatching { app.persona.brief() }.getOrDefault("")
        val prompt = buildString {
            if (viewer.isNotBlank()) {
                appendLine(viewer)
                appendLine()
            }
            appendLine("Pick ONE film for someone tonight. They want ${mood.line}" + (time.minutes?.let { ", and have under $it minutes." } ?: "."))
            if (loved.isNotEmpty()) appendLine("Films they loved: ${loved.joinToString("; ")}.")
            appendLine("Choose from their own watchlist:")
            best.forEachIndexed { index, (item, runtime, _) ->
                appendLine("${index + 1}. ${item.title} (${item.year}), ${runtime}m, rated ${"%.1f".format(item.rating)}")
            }
            appendLine("Reply with JSON only: {\"pick\": number from the list, \"why\": one or two sentences to them, under 35 words, no spoilers}.")
        }
        val chosen = app.gemini.json(prompt)?.let { raw ->
            runCatching {
                val obj = com.cineverse.app.core.net.Http.json.parseToJsonElement(Gemini.extractJson(raw) ?: return@runCatching null)
                    as kotlinx.serialization.json.JsonObject
                val index = (obj["pick"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()?.minus(1) ?: return@runCatching null
                val why = (obj["why"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim().orEmpty()
                best.getOrNull(index)?.let { (item, runtime, _) -> TonightPick(item.asItem(), runtime, why, byGemini = true) }
                    ?.takeIf { why.length > 8 }
            }.getOrNull()
        }
        chosen ?: best.first().let { (item, runtime, _) ->
            TonightPick(item.asItem(), runtime, reason(item, runtime, mood), byGemini = false)
        }
    }

    private fun reason(item: com.cineverse.app.data.model.SavedItem, runtime: Int, mood: Mood): String {
        val genre = item.genres.firstOrNull { it in mood.genres }?.let { com.cineverse.app.data.model.GenreNames[it] }?.lowercase()
        val length = if (runtime > 0) "${runtime / 60}h ${runtime % 60}m" else null
        val since = item.addedAt.takeIf { it > 0 }?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
        }
        return buildString {
            append("A ")
            if (length != null) append("$length ")
            append(genre ?: "film")
            if (item.rating > 0) append(" rated ${"%.1f".format(item.rating)}")
            if (since != null) append(", on your list since $since")
            append(".")
        }
    }
}
