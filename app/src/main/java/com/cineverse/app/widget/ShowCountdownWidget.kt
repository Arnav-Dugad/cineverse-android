package com.cineverse.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
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
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.cineverse.app.CineVerseApp
import com.cineverse.app.MainActivity
import com.cineverse.app.R
import com.cineverse.app.data.airing.Airing
import com.cineverse.app.data.airing.UpNextItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One show's next episode, counting down on the home screen: a reminder you
 * can see. Pinned from the show's page ("Add countdown to Home screen"), so
 * each one is the show you asked for. Inside a day it ticks to the second -
 * the launcher's own chronometer, with the app asleep - and further out it
 * gives the days and the date. Added from the widget picker instead, it
 * follows whichever show is due soonest.
 */
class ShowCountdownWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as CineVerseApp).container
        val widgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrDefault(0)
        val chosen = ShowCountdowns.showFor(context, widgetId)
        val item: UpNextItem? = runCatching {
            if (chosen > 0) {
                val brief = container.tmdb.tvBrief(chosen)
                brief?.let { b ->
                    val stamp = runCatching { container.airing.times.lookup(b) }.getOrNull()
                    Airing.upNext(b, stamp)
                }
            } else {
                container.airing.upNext(container.episodes.progress.first()).firstOrNull()
            }
        }.getOrNull()
        val art = item?.let { WidgetArt.backdrop(context, it.show.backdrop) ?: WidgetArt.poster(context, it.show.poster, 0f) }
        // Redraw the moment it airs.
        item?.let { soon ->
            val wait = soon.at - System.currentTimeMillis()
            if (wait in 1..86_400_000L) runCatching {
                androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                    "show_countdown_refresh_$widgetId",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    androidx.work.OneTimeWorkRequestBuilder<UpNextRefreshWorker>()
                        .setInitialDelay(wait + 2_000, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .build(),
                )
            }
        }
        provideContent {
            GlanceTheme {
                Box(
                    GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.widgetBackground)
                        .cornerRadius(26.dp)
                        .clickable(
                            actionStartActivity(
                                Intent(context, MainActivity::class.java)
                                    .setAction(Intent.ACTION_VIEW)
                                    .apply { item?.let { setData(android.net.Uri.parse("cineverse://tv/${it.show.id}")) } }
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        ),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    if (art != null) {
                        Image(ImageProvider(art), item.show.name, contentScale = ContentScale.Crop, modifier = GlanceModifier.fillMaxSize())
                        Image(ImageProvider(R.drawable.widget_hero_scrim), null, contentScale = ContentScale.FillBounds, modifier = GlanceModifier.fillMaxSize())
                    }
                    Column(GlanceModifier.padding(14.dp)) {
                        if (item == null) {
                            Text(
                                if (chosen > 0) "No date for the next episode yet" else "Nothing due in the next month",
                                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                            )
                            return@Column
                        }
                        val white = ColorProvider(androidx.compose.ui.graphics.Color.White)
                        Text(
                            item.show.name,
                            style = TextStyle(color = white, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1,
                        )
                        Text(
                            "S${item.next.season} E${item.next.episode} · ${item.kind}",
                            style = TextStyle(color = ColorProvider(androidx.compose.ui.graphics.Color(0xD9FFFFFF)), fontSize = 11.sp),
                            maxLines = 1,
                        )
                        Spacer(GlanceModifier.height(4.dp))
                        BigCountdown(context, item)
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun BigCountdown(context: Context, item: UpNextItem) {
        val now = System.currentTimeMillis()
        val left = item.at - now
        if (item.exact && left in 1..86_400_000L) {
            AndroidRemoteViews(
                RemoteViews(context.packageName, R.layout.widget_countdown_large).apply {
                    setChronometer(R.id.countdown, SystemClock.elapsedRealtime() + left, null, true)
                    setChronometerCountDown(R.id.countdown, true)
                }
            )
        } else {
            val text = Airing.countdown(item, now)
            Text(
                text,
                style = TextStyle(
                    color = ColorProvider(if (text == "Out now") androidx.compose.ui.graphics.Color(0xFF34D399) else androidx.compose.ui.graphics.Color(0xFFFBBF24)),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            if (text != "Out now") {
                Text(
                    airLabel(item),
                    style = TextStyle(color = ColorProvider(androidx.compose.ui.graphics.Color(0xB3FFFFFF)), fontSize = 11.sp),
                )
            }
        }
    }
}

class ShowCountdownReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShowCountdownWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { ShowCountdowns.forget(context, it) }
    }
}

/** Which show each countdown widget is for, and pinning a new one. */
object ShowCountdowns {
    private const val PREFS = "show_countdowns"
    const val EXTRA_SHOW = "showId"

    fun showFor(context: Context, widgetId: Int): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("w$widgetId", 0)

    fun remember(context: Context, widgetId: Int, showId: Int) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("w$widgetId", showId).apply()

    fun forget(context: Context, widgetId: Int) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("w$widgetId").apply()

    /** True when this launcher can be asked to place a widget. */
    fun canPin(context: Context): Boolean =
        runCatching { AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported }.getOrDefault(false)

    /**
     * Ask the launcher to place a countdown for [showId]. The launcher shows
     * its own confirmation; once placed, it tells [PinnedReceiver] the new
     * widget's id, which is when the show is attached to it.
     */
    fun pin(context: Context, showId: Int): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val callback = PendingIntent.getBroadcast(
            context,
            showId,
            Intent(context, PinnedReceiver::class.java).putExtra(EXTRA_SHOW, showId),
            // Mutable: the launcher fills in the new widget's id.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        return manager.requestPinAppWidget(ComponentName(context, ShowCountdownReceiver::class.java), null, callback)
    }
}

/** The launcher has placed a countdown: attach its show and draw it. */
class PinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
        val showId = intent.getIntExtra(ShowCountdowns.EXTRA_SHOW, 0)
        if (widgetId == 0 || showId == 0) return
        ShowCountdowns.remember(context, widgetId, showId)
        val pending = goAsync()
        val container = (context.applicationContext as CineVerseApp).container
        container.scope.launch {
            try {
                runCatching {
                    val manager = GlanceAppWidgetManager(context)
                    manager.getGlanceIds(ShowCountdownWidget::class.java)
                        .firstOrNull { manager.getAppWidgetId(it) == widgetId }
                        ?.let { ShowCountdownWidget().update(context, it) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** "Tue 6 Oct, 21:00", or the date alone where only the date is known. A widget redraws itself on a language change. */
private fun airLabel(item: UpNextItem): String =
    Instant.ofEpochMilli(item.at).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern(if (item.exact) "EEE d MMM, HH:mm" else "EEE d MMM", Locale.getDefault()))
