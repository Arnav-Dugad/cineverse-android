package com.cineverse.app.data.ai

import android.content.Context
import com.cineverse.app.AppContainer
import com.cineverse.app.core.net.Http
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * A Home row for this moment, written by Gemini: "For a slow Sunday
 * morning", "Late-night weeknight thrillers" - the time and day where you
 * are, your taste, and a dozen titles you have not seen that suit it.
 * Written once per part of the day and kept until the next part begins.
 */
class MomentRail(private val app: AppContainer) {

    data class Moment(val title: String, val items: List<MediaItem>)

    private val prefs = app.context.getSharedPreferences("moment_rail", Context.MODE_PRIVATE)

    /** "morning", "afternoon", "evening", "late night" - and whether it is the weekend. */
    private fun part(now: LocalDateTime): String {
        val hour = now.hour
        val time = when (hour) {
            in 5..11 -> "morning"
            in 12..16 -> "afternoon"
            in 17..21 -> "evening"
            else -> "late night"
        }
        val day = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
        return "$day $time"
    }

    suspend fun now(): Moment? {
        val now = LocalDateTime.now()
        // Past midnight still belongs to the evening before.
        val date = if (now.hour < 5) LocalDate.now().minusDays(1) else LocalDate.now()
        val key = "$date ${part(now)}"
        cached(key)?.let { return it }
        val brief = runCatching { app.persona.brief() }.getOrDefault("")
        if (brief.isBlank()) return null
        val weekend = now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY
        val prompt = buildString {
            appendLine(brief)
            appendLine()
            appendLine("It is ${part(now)} (${"%02d:%02d".format(now.hour, now.minute)}) where the viewer is${if (weekend) ", the weekend" else ""}.")
            appendLine("Write one row for their Home screen for exactly this moment. Its title: 3 to 7 words, warm and specific to")
            appendLine("the moment and their taste, like \"For a slow Sunday morning\" or \"Late-night weeknight thrillers\" (write your own).")
            appendLine("Then pick 14 films or series they have NOT watched that suit this moment - the length, the energy, the mood")
            appendLine("of this time of day - and their taste. Varied, real, findable on TMDB.")
            appendLine("Reply with JSON only: {\"title\": string, \"titles\": [{\"title\": string, \"year\": integer, \"type\": \"movie\" or \"tv\"}]}")
        }
        val raw = app.gemini.json(prompt, timeoutMs = 20_000) ?: return null
        val parsed = runCatching {
            val obj = Http.json.parseToJsonElement(Gemini.extractJson(raw) ?: return null).jsonObject
            val title = obj["title"]?.jsonPrimitive?.contentOrNull?.let(Gemini::plain)?.trim()?.trim('"') ?: return null
            val wanted = (obj["titles"] as? JsonArray).orEmpty().mapNotNull { element ->
                val row = element.jsonObject
                val name = row["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                Triple(name, row["year"]?.jsonPrimitive?.intOrNull, row["type"]?.jsonPrimitive?.contentOrNull)
            }
            title to wanted
        }.getOrNull() ?: return null
        val library = app.library.library.value
        val items = coroutineScope {
            parsed.second.map { (name, year, type) ->
                async {
                    val media = when (type) { "tv" -> MediaType.Tv; "movie" -> MediaType.Movie; else -> null }
                    runCatching { app.tmdb.searchPage(name, 1, false).items }.getOrDefault(emptyList())
                        .filter { (media == null || it.type == media) && it.hasArt }
                        .firstOrNull { year == null || it.year.toIntOrNull()?.let { y -> kotlin.math.abs(y - year) <= 1 } != false }
                }
            }.awaitAll()
        }.filterNotNull().distinctBy { it.key }.filterNot { library.isWatched(it.key) }.take(12)
        if (items.size < 4) return null
        val moment = Moment(parsed.first, items)
        save(key, moment)
        return moment
    }

    private fun cached(key: String): Moment? {
        val raw = prefs.getString("moment", null) ?: return null
        val lines = raw.lines()
        if (lines.firstOrNull() != key || lines.size < 3) return null
        val items = lines.drop(2).mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 6) return@mapNotNull null
            MediaItem(
                id = p[1].toIntOrNull() ?: return@mapNotNull null,
                type = MediaType.of(p[0]),
                title = p[2],
                posterPath = p[3].ifBlank { null },
                releaseDate = p[4],
                voteAverage = p[5].toDoubleOrNull() ?: 0.0,
            )
        }
        return Moment(lines[1], items)
    }

    private fun save(key: String, moment: Moment) {
        val body = buildString {
            appendLine(key)
            appendLine(moment.title)
            moment.items.forEach { item ->
                appendLine(listOf(item.type.wire, item.id, item.title.replace('\t', ' '), item.posterPath.orEmpty(), item.releaseDate, item.voteAverage).joinToString("\t"))
            }
        }.trimEnd()
        prefs.edit().putString("moment", body).apply()
    }
}
