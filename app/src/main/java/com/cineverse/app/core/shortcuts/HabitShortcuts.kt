package com.cineverse.app.core.shortcuts

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.cineverse.app.core.ui.Img
import com.cineverse.app.data.model.ShowProgress
import com.cineverse.app.data.recap.Recaps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

/**
 * Launcher shortcuts that follow your habits.
 *
 * Long-press the CineVerse icon on a Sunday evening and "Continue The Bear"
 * is there, because Sunday evening is when you watch The Bear. The ranking is
 * your own sittings: a show scores for every recent sitting near this hour on
 * the same kind of day - weekday or weekend - with recent weeks counting most.
 * Only shows with an episode actually waiting are offered.
 */
object HabitShortcuts {

    private const val PREFIX = "habit_"
    private const val MAX = 2

    /** The shows most likely to be wanted at [now], best first. Pure, for tests. */
    fun rank(shows: Collection<ShowProgress>, now: ZonedDateTime, limit: Int = MAX): List<ShowProgress> {
        val weekend = now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY
        val hour = now.hour + now.minute / 60.0
        return shows
            .filter { !it.dropped && it.nextUp() != null && it.title.isNotBlank() }
            .map { show ->
                val score = Recaps.sittings(show).distinct().sumOf { at ->
                    val moment = Instant.ofEpochMilli(at).atZone(now.zone)
                    val ageDays = (now.toInstant().toEpochMilli() - at) / 86_400_000.0
                    if (ageDays < 0 || ageDays > 90) return@sumOf 0.0
                    val sittingWeekend = moment.dayOfWeek == DayOfWeek.SATURDAY || moment.dayOfWeek == DayOfWeek.SUNDAY
                    val diff = abs(moment.hour + moment.minute / 60.0 - hour)
                    val hours = min(diff, 24 - diff)
                    val nearHour = exp(-(hours * hours) / 4.5)          // ±2h matters, ±5h barely
                    val sameKind = if (sittingWeekend == weekend) 1.0 else 0.35
                    val recency = exp(-ageDays / 30.0)
                    nearHour * sameKind * recency
                }
                show to score
            }
            .filter { it.second > 0.15 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    /** Rebuild the dynamic shortcuts for this moment. Safe to call often. */
    suspend fun refresh(context: Context, shows: Collection<ShowProgress>) = withContext(Dispatchers.Default) {
        runCatching {
            val picks = rank(shows, ZonedDateTime.now(ZoneId.systemDefault()))
            val built = picks.mapIndexed { index, show ->
                val next = show.nextUp()
                val label = when {
                    next == null -> "Continue"
                    show.isAbsolute -> "EP ${next.second}"
                    else -> "S${next.first} E${next.second}"
                }
                val icon = poster(context, show.poster)?.let { IconCompat.createWithAdaptiveBitmap(adaptive(it)) }
                    ?: IconCompat.createWithResource(context, com.cineverse.app.R.mipmap.ic_launcher)
                ShortcutInfoCompat.Builder(context, PREFIX + show.tmdbId)
                    .setShortLabel(show.title.take(24))
                    .setLongLabel("Continue ${show.title} · $label".take(48))
                    .setIcon(icon)
                    .setRank(index)
                    .setIntent(
                        Intent(Intent.ACTION_VIEW, Uri.parse("cineverse://tv/${show.tmdbId}"))
                            .setPackage(context.packageName)
                    )
                    .build()
            }
            // Old habit shortcuts out, the current ones in; the static three stay.
            val stale = ShortcutManagerCompat.getDynamicShortcuts(context)
                .map { it.id }.filter { it.startsWith(PREFIX) && it !in built.map { b -> b.id } }
            if (stale.isNotEmpty()) ShortcutManagerCompat.removeDynamicShortcuts(context, stale)
            built.forEach { ShortcutManagerCompat.pushDynamicShortcut(context, it) }
        }
    }

    /** Tell the launcher a habit shortcut's show was opened, which helps its own ranking. */
    fun reportOpened(context: Context, showId: Int) {
        runCatching { ShortcutManagerCompat.reportShortcutUsed(context, PREFIX + showId) }
    }

    private suspend fun poster(context: Context, path: String): Bitmap? {
        if (path.isBlank()) return null
        return runCatching {
            val request = ImageRequest.Builder(context).data(Img.poster(path)).allowHardware(false).build()
            SingletonImageLoader.get(context).execute(request).image?.toBitmap()
        }.getOrNull()
    }

    /**
     * An adaptive icon is a 108dp square whose middle 72dp is what shows, so
     * the poster's centre is scaled to fill that square edge to edge.
     */
    private fun adaptive(source: Bitmap): Bitmap {
        val size = 216
        val side = min(source.width, source.height)
        val x = (source.width - side) / 2
        val y = (source.height - side) / 3
        val square = Bitmap.createBitmap(source, x, y.coerceAtLeast(0), side, side)
        return Bitmap.createScaledBitmap(square, size, size, true)
    }
}
