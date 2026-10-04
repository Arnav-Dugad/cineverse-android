package com.cineverse.app.data.charts

import android.content.Context
import androidx.compose.runtime.Immutable
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import java.time.LocalDate
import java.time.temporal.IsoFields

/** Where a title sat in a weekly Top 10: week label to rank (1-10). */
@Immutable
data class ChartRun(val chart: MediaType, val weeks: List<Pair<String, Int?>>, val thisWeek: String = "") {
    /** Whether this week's chart has been seen at all - without it, "this week" is unknown. */
    val current: Boolean get() = weeks.lastOrNull()?.first == thisWeek
    val ranked: List<Pair<String, Int>> get() = weeks.mapNotNull { (w, r) -> r?.let { w to it } }
    val best: Int? get() = ranked.minOfOrNull { it.second }
    val now: Int? get() = if (current) weeks.lastOrNull()?.second else null
    val any: Boolean get() = ranked.isNotEmpty()
}

/**
 * The Top 10's past, as the website keeps it: TMDB only publishes this
 * week's chart, so every week the app sees the chart (from the Top 10 page,
 * Home, or the twice-daily background check) it writes down that week's ten,
 * keyed by ISO week. Kept for a year; a title page reads back where it sat.
 */
class TopTenHistory(context: Context) {

    private val prefs = context.getSharedPreferences("top10_history", Context.MODE_PRIVATE)

    fun weekKey(date: LocalDate = LocalDate.now()): String =
        "%d-W%02d".format(date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))

    /** This week's ten, for a chart. The latest sighting in a week wins. */
    fun record(chart: MediaType, items: List<MediaItem>) {
        if (items.size < 5) return
        val week = weekKey()
        val editor = prefs.edit().putString("${chart.wire}|$week", items.take(10).joinToString(",") { it.id.toString() })
        // A year of weeks, the oldest dropped.
        prefs.all.keys.filter { it.startsWith("${chart.wire}|") }.sorted().dropLast(53).forEach { editor.remove(it) }
        if (!prefs.contains("since")) editor.putString("since", LocalDate.now().toString())
        editor.apply()
    }

    /** Since when the app has been keeping the chart, if it has. */
    fun since(): LocalDate? = prefs.getString("since", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** The last [weeks] weeks of a title's chart history, oldest first. */
    fun of(id: Int, chart: MediaType, weeks: Int = 12): ChartRun {
        val keys = prefs.all.keys.filter { it.startsWith("${chart.wire}|") }.sorted().takeLast(weeks)
        val run = keys.map { key ->
            val ids = prefs.getString(key, "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }
            val rank = ids.indexOf(id).takeIf { it >= 0 }?.plus(1)
            key.substringAfter('|') to rank
        }
        return ChartRun(chart, run, weekKey())
    }
}
