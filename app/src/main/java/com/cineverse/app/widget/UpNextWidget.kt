package com.cineverse.app.widget

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.cineverse.app.CineVerseApp
import com.cineverse.app.MainActivity
import com.cineverse.app.R
import com.cineverse.app.data.airing.Airing
import com.cineverse.app.data.airing.UpNextItem
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Up Next, on the home screen: the shows you are caught up on, counting down
 * to the episodes they are waiting for.
 *
 * Inside a day the countdown is LIVE - a native Chronometer in countdown mode,
 * which the launcher ticks itself, so the seconds move with the app asleep and
 * the battery untouched. Further out it says the day. The list is rebuilt every
 * half hour and whenever the app refreshes its airing data.
 */
class UpNextWidget : GlanceAppWidget() {

    override val sizeMode = androidx.glance.appwidget.SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as CineVerseApp).container
        val shows = container.episodes.progress.first()
        val items = runCatching { container.airing.upNext(shows) }.getOrDefault(emptyList()).take(5)
        val art = kotlinx.coroutines.coroutineScope {
            items.map { item ->
                async { item.show.id to WidgetArt.poster(context, item.show.poster) }
            }.mapNotNull { job -> job.await().let { (key, bitmap) -> bitmap?.let { key to it } } }.toMap()
        }
        // A Chronometer counts on past zero into negative numbers, so the
        // widget redraws itself the moment the soonest episode airs.
        items.map { it.at - System.currentTimeMillis() }.filter { it in 1..86_400_000L }.minOrNull()?.let { wait ->
            runCatching {
                androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                    "upnext_widget_refresh",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    androidx.work.OneTimeWorkRequestBuilder<UpNextRefreshWorker>()
                        .setInitialDelay(wait + 2_000, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .build(),
                )
            }
        }
        provideContent {
            GlanceTheme { Body(context, items, art) }
        }
    }

    @Composable
    private fun Body(context: Context, items: List<UpNextItem>, art: Map<Int, android.graphics.Bitmap>) {
        Column(
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(28.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                "Up next",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.clickable(actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))),
            )
            Spacer(GlanceModifier.height(10.dp))
            if (items.isEmpty()) {
                Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nothing due in the next month.\nCatch up on a show and its next episode counts down here.",
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                    )
                }
                return@Column
            }
            LazyColumn {
                items(items, itemId = { it.show.id.toLong() }) { item ->
                    Row(
                        GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clickable(
                                actionStartActivity(
                                    Intent(context, MainActivity::class.java)
                                        .setAction(Intent.ACTION_VIEW)
                                        .setData(android.net.Uri.parse("cineverse://tv/${item.show.id}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            GlanceModifier
                                .size(width = 42.dp, height = 63.dp)
                                .cornerRadius(10.dp)
                                .background(GlanceTheme.colors.surfaceVariant),
                        ) {
                            art[item.show.id]?.let {
                                Image(ImageProvider(it), item.show.name, contentScale = ContentScale.Crop, modifier = GlanceModifier.fillMaxSize())
                            }
                        }
                        Spacer(GlanceModifier.width(11.dp))
                        Column(GlanceModifier.defaultWeight()) {
                            Text(
                                item.show.name,
                                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                                maxLines = 1,
                            )
                            Text(
                                "S${item.next.season} E${item.next.episode} · ${item.kind}",
                                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                                maxLines = 1,
                            )
                            Spacer(GlanceModifier.height(3.dp))
                            Countdown(context, item)
                        }
                    }
                }
            }
        }
    }

    /**
     * Under a day away: a live, ticking countdown. Further out, or out
     * already, or only a date known for tomorrow: words.
     */
    @Composable
    private fun Countdown(context: Context, item: UpNextItem) {
        val now = System.currentTimeMillis()
        val left = item.at - now
        if (item.exact && left in 1..86_400_000L) {
            val views = RemoteViews(context.packageName, R.layout.widget_countdown).apply {
                setChronometer(R.id.countdown, SystemClock.elapsedRealtime() + left, null, true)
                setChronometerCountDown(R.id.countdown, true)
            }
            AndroidRemoteViews(views)
        } else {
            val text = Airing.countdown(item, now)
            val day = runCatching { LocalDate.parse(item.next.airDate) }.getOrNull()
            Text(
                if (text == "Out now") "Out now" else if (day != null && text.startsWith("In ")) "$text · ${day.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}" else text,
                style = TextStyle(
                    color = ColorProvider(if (text == "Out now") androidx.compose.ui.graphics.Color(0xFF34D399) else androidx.compose.ui.graphics.Color(0xFFFBBF24)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

class UpNextWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpNextWidget()
}

/** Redraws the Up Next widget when a countdown reaches zero. */
class UpNextRefreshWorker(
    context: Context,
    params: androidx.work.WorkerParameters,
) : androidx.work.CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        UpNextWidget().updateAllSafe(applicationContext)
        return Result.success()
    }
}

/** Every placed Up Next widget, redrawn; harmless when none is placed. */
suspend fun UpNextWidget.updateAllSafe(context: Context) {
    runCatching { androidx.glance.appwidget.GlanceAppWidgetManager(context).getGlanceIds(UpNextWidget::class.java).forEach { update(context, it) } }
}
