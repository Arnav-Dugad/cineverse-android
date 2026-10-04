package com.cineverse.app.data.ai

import android.content.Context
import com.cineverse.app.core.net.Http
import com.cineverse.app.data.diary.DiaryEntry
import com.cineverse.app.data.model.GenreNames
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.YearMonth

/**
 * A line for a month in the Diary, from Gemini: "A month of comfort
 * rewatches", "Korean thrillers, mostly after midnight". Written from what
 * was actually watched that month, and kept until the month changes.
 */
class MonthBlurb(context: Context, private val gemini: Gemini) {

    private val prefs = context.getSharedPreferences("diary_blurbs", Context.MODE_PRIVATE)

    /** The signature a blurb was written for: a month that gains a viewing is written again. */
    private fun signature(month: YearMonth, entries: List<DiaryEntry>) = "v2:$month:${entries.size}"

    fun cached(month: YearMonth, entries: List<DiaryEntry>): String? =
        prefs.getString(signature(month, entries), null)

    suspend fun blurb(month: YearMonth, entries: List<DiaryEntry>): String? {
        if (entries.size < 3) return null
        cached(month, entries)?.let { return it }
        val zone = java.time.ZoneId.systemDefault()
        val lines = entries.sortedBy { it.at }.take(60).joinToString("\n") { entry ->
            val time = java.time.Instant.ofEpochMilli(entry.at).atZone(zone)
            buildString {
                append("- ${entry.item.title}")
                if (entry.episode.isNotBlank()) append(" ${entry.episode}")
                entry.item.genreIds.mapNotNull { GenreNames[it] }.take(2).takeIf { it.isNotEmpty() }?.let { append(" [${it.joinToString()}]") }
                if (entry.rewatch) append(" (rewatch)")
                append(" on ${time.dayOfWeek.name.lowercase()} at ${time.hour}:00")
            }
        }
        val prompt = """
            Here is everything someone watched in ${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${month.year}:
            $lines

            Sum the month up as a short, warm, slightly witty caption of 3 to 8 words, the way a friend would.
            The kind of thing meant (write your own, never one of these): "A month of comfort rewatches",
            "Thrillers, mostly after midnight", "One show, many late nights".
            Notice what stands out: rewatches, a genre or language, one show taking over, late nights, weekends -
            and name the show or film when one dominates.
            Reply with JSON only: {"blurb": string}. No quotation marks inside it, no emoji.
        """.trimIndent()
        val raw = gemini.json(prompt) ?: return null
        val blurb = runCatching {
            Http.json.parseToJsonElement(Gemini.extractJson(raw) ?: return null).jsonObject["blurb"]
                ?.jsonPrimitive?.contentOrNull?.let(Gemini::plain)?.trim()?.trim('"', '.', '“', '”')
        }.getOrNull()?.takeIf { it.length in 6..70 } ?: return null
        prefs.edit().putString(signature(month, entries), blurb).apply()
        return blurb
    }
}
