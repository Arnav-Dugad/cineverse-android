package com.cineverse.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.cineverse.app.CineVerseApp
import com.cineverse.app.MainActivity
import com.cineverse.app.core.design.Palette
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Today, on the home screen.
 *
 * The companion to [ContinueWidget], and deliberately a different SHAPE of
 * thing. That one is a list you act on; this one is a glance you do not touch —
 * a 2x1 tile with the streak, what today has added up to, and nothing to tap
 * except the way in.
 *
 * A streak is the one statistic that changes the behaviour it measures, which is
 * exactly why it belongs on a home screen and the total hours watched does not.
 * Seeing "4 days" at nine in the evening is a nudge; seeing "733 titles" is
 * trivia.
 *
 * The honest part: it never scolds. A broken streak reads "Start one tonight",
 * not a flame going out. An app that makes someone feel bad for not watching
 * television has lost its sense of proportion.
 */
class TodayWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as CineVerseApp).container
        val library = container.library.library.first { it.loaded }
        val shows = container.episodes.progress.first()

        val todayStart = startOfDay(System.currentTimeMillis())

        // Every day anything was marked, films and episodes alike, as day keys.
        val days = buildSet {
            library.watched.values.forEach { if (it.watchedAt > 0) add(startOfDay(it.watchedAt)) }
            shows.values.forEach { show ->
                show.log.forEach { row -> if (row.stamp > 0) add(startOfDay(row.stamp)) }
            }
        }

        val episodesToday = shows.values.sumOf { show ->
            show.log.count { startOfDay(it.stamp) == todayStart }
        }
        val filmsToday = library.watched.values.count {
            it.type == com.cineverse.app.data.model.MediaType.Movie &&
                startOfDay(it.watchedAt) == todayStart
        }

        provideContent {
            GlanceTheme {
                Body(
                    streak = streak(days, todayStart),
                    episodes = episodesToday,
                    films = filmsToday,
                    watchedToday = days.contains(todayStart),
                )
            }
        }
    }

    @Composable
    private fun Body(streak: Int, episodes: Int, films: Int, watchedToday: Boolean) {
        Column(
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(24.dp)
                .padding(14.dp)
                // Opens Stats, through the same cineverse:// scheme the
                // shortcuts and the other widget use. Inventing a private
                // action for this would mean a second routing table to keep in
                // step with the first.
                .clickable(
                    actionStartActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            android.net.Uri.parse("cineverse://stats"),
                            androidx.glance.LocalContext.current,
                            MainActivity::class.java,
                        )
                    )
                ),
        ) {
            Text(
                if (streak > 0) "STREAK" else "CINEVERSE",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Spacer(GlanceModifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (streak > 0) streak.toString() else "–",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                if (streak > 0) {
                    Spacer(GlanceModifier.width(5.dp))
                    Text(
                        if (streak == 1) "day" else "days",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 13.sp,
                        ),
                    )
                }
            }
            Spacer(GlanceModifier.height(6.dp))
            Text(
                today(episodes, films, watchedToday, streak),
                maxLines = 2,
                style = TextStyle(
                    color = if (watchedToday) GlanceTheme.colors.onSurfaceVariant
                else androidx.glance.unit.ColorProvider(Palette.Red2),
                    fontSize = 12.sp,
                ),
            )
        }
    }
}

private val Int.sp get() = androidx.compose.ui.unit.TextUnit(
    toFloat(),
    androidx.compose.ui.unit.TextUnitType.Sp,
)

private fun today(episodes: Int, films: Int, watchedToday: Boolean, streak: Int): String {
    if (!watchedToday) {
        return if (streak > 0) "Nothing today yet — keep it going" else "Start one tonight"
    }
    val parts = buildList {
        if (episodes > 0) add("$episodes episode${if (episodes == 1) "" else "s"}")
        if (films > 0) add("$films film${if (films == 1) "" else "s"}")
    }
    return if (parts.isEmpty()) "Watched today" else parts.joinToString(" · ") + " today"
}

private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/**
 * Days in a row, counting back.
 *
 * Starts from YESTERDAY when nothing has been watched today, so a streak does
 * not appear to break at midnight and come back in the evening. That one rule is
 * the difference between a number people trust and one they argue with.
 */
private fun streak(days: Set<Long>, todayStart: Long): Int {
    if (days.isEmpty()) return 0
    val day = 24L * 60 * 60 * 1000
    var cursor = if (days.contains(todayStart)) todayStart else todayStart - day
    if (!days.contains(cursor)) return 0
    var count = 0
    while (days.contains(cursor)) {
        count++
        cursor -= day
    }
    return count
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
