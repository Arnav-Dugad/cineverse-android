package com.cineverse.app.data.ai

import androidx.compose.runtime.Immutable
import com.cineverse.app.AppContainer
import com.cineverse.app.core.net.Http
import com.cineverse.app.data.model.TitleDetail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Two titles, side by side. */
@Immutable
data class Comparison(
    val rows: List<Row>,
    /** Which to watch, for this viewer, and why. */
    val verdict: String,
    /** 0 the first, 1 the second, -1 neither over the other. */
    val pick: Int,
) {
    @Immutable
    data class Row(val aspect: String, val first: String, val second: String)
}

/**
 * "Compare with...": Gemini sets two titles against each other on the same
 * handful of points - tone, pace, length, what critics made of it, who it is
 * for - in a few words each, then says which this viewer should watch, from
 * their own brief. Spoiler-free on both sides.
 */
class Compare(private val app: AppContainer) {

    suspend fun of(first: TitleDetail, second: TitleDetail): Comparison? {
        val viewer = runCatching { app.persona.brief() }.getOrDefault("")
        fun describe(detail: TitleDetail) = buildString {
            append("${detail.title} (${detail.releaseDate.take(4)}), ${if (detail.isSeries) "series" else "film"}")
            if (detail.genres.isNotEmpty()) append("; ${detail.genres.joinToString { it.name }}")
            if (detail.runtime > 0) append("; ${detail.runtime} min")
            if (detail.isSeries) append("; ${detail.numberOfSeasons} seasons, ${detail.numberOfEpisodes} episodes")
            detail.director?.let { append("; directed by ${it.name}") }
            if (detail.cast.isNotEmpty()) append("; starring ${detail.cast.take(4).joinToString { it.name }}")
            if (detail.voteAverage > 0) append("; TMDB ${"%.1f".format(detail.voteAverage)}")
            if (detail.overview.isNotBlank()) append(". Premise: ${detail.overview.take(400)}")
        }
        val prompt = buildString {
            if (viewer.isNotBlank()) {
                appendLine(viewer)
                appendLine()
            }
            appendLine("Compare these two titles for this viewer, without spoilers beyond each premise.")
            val library = app.library.library.value
            fun seen(detail: TitleDetail): String {
                val rated = library.ratings[detail.key]
                return when {
                    library.isWatched(detail.key) || (app.episodes.progress.value[detail.id]?.watchedCount ?: 0) > 0 ->
                        " [the viewer HAS watched this${rated?.let { r -> ", rated $r/10" } ?: ""}]"
                    else -> " [not watched yet]"
                }
            }
            appendLine("A: ${describe(first)}${seen(first)}")
            appendLine("B: ${describe(second)}${seen(second)}")
            appendLine()
            appendLine("Reply with JSON only:")
            appendLine("{\"rows\": [{\"aspect\": string, \"a\": string, \"b\": string}], \"verdict\": string, \"pick\": 0 | 1 | -1}")
            appendLine("rows: exactly 6, in this order of aspects: Tone, Pace, Commitment (how long it takes to watch), Critics, Best for, Standout.")
            appendLine("Each a and b at most 7 words, concrete, no full sentences needed.")
            appendLine("verdict: two sentences to the viewer, under 40 words, which to watch next and why, using their own taste (name one of their titles).")
            appendLine("If they have already watched one, the question is whether the other is worth it (or which deserves a rewatch); never tell them to watch something they have seen as if it were new.")
            appendLine("pick: 0 for A, 1 for B, -1 only if truly even. No preamble, no markdown.")
        }
        val raw = app.gemini.json(prompt, timeoutMs = 20_000) ?: return null
        return runCatching {
            val obj = Http.json.parseToJsonElement(Gemini.extractJson(raw) ?: return null).jsonObject
            val rows = (obj["rows"] as? JsonArray).orEmpty().mapNotNull { element ->
                val row = element.jsonObject
                val aspect = row["aspect"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                Comparison.Row(
                    aspect,
                    row["a"]?.jsonPrimitive?.contentOrNull.orEmpty().let(Gemini::plain),
                    row["b"]?.jsonPrimitive?.contentOrNull.orEmpty().let(Gemini::plain),
                )
            }
            val verdict = obj["verdict"]?.jsonPrimitive?.contentOrNull?.let(Gemini::plain).orEmpty()
            if (rows.size < 3 || verdict.isBlank()) return null
            Comparison(rows.take(6), verdict, obj["pick"]?.jsonPrimitive?.intOrNull?.coerceIn(-1, 1) ?: -1)
        }.getOrNull()
    }
}
