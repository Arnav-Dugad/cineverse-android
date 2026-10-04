package com.cineverse.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.cineverse.app.CineVerseApp
import com.cineverse.app.MainActivity
import com.cineverse.app.data.airing.UpNextItem
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * The next episode, for the lock screen (and the home screen): the show, the
 * episode, and how long until it airs - "Tomorrow, 21:00", "in 4 days" - in
 * big type on a see-through glass card, so it reads at a glance over any
 * wallpaper. A tap opens the show.
 */
class NextEpisodeWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as CineVerseApp).container
        val next: UpNextItem? = runCatching {
            container.airing.upNext(lockScreenShows(container)).firstOrNull()
        }.getOrNull()
        provideContent { GlanceTheme { Body(next) } }
    }

    @Composable
    private fun Body(next: UpNextItem?) {
        val white = ColorProvider(day = androidx.compose.ui.graphics.Color.White, night = androidx.compose.ui.graphics.Color.White)
        val dim = ColorProvider(day = androidx.compose.ui.graphics.Color(0xCCFFFFFF), night = androidx.compose.ui.graphics.Color(0xCCFFFFFF))
        val red = ColorProvider(day = androidx.compose.ui.graphics.Color(0xFFFF4757), night = androidx.compose.ui.graphics.Color(0xFFFF4757))
        val link = next?.let { "cineverse://tv/${it.show.id}" } ?: "cineverse://list"
        Column(
            GlanceModifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color(0x66101016))
                .cornerRadius(22.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .clickable(
                    actionStartActivity(
                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link), androidx.glance.LocalContext.current, MainActivity::class.java)
                    )
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (next == null) {
                Text("No episodes on the way", style = TextStyle(color = white, fontSize = 15.sp, fontWeight = FontWeight.Medium))
                Text("Shows you're caught up on appear here", style = TextStyle(color = dim, fontSize = 12.sp))
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NEXT EPISODE", style = TextStyle(color = red, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.width(6.dp))
                Text("S${next.next.season} E${next.next.episode}", style = TextStyle(color = dim, fontSize = 10.sp, fontWeight = FontWeight.Medium))
            }
            Text(next.show.name, style = TextStyle(color = white, fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(whenLabel(next), style = TextStyle(color = dim, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
    }
}

/**
 * Shows a widget may name. Widgets sit on the lock screen, so a show from a
 * locked list (or an adult one) is never among them. Which those are is only
 * known once the library has loaded; until then there are none.
 */
internal suspend fun lockScreenShows(container: com.cineverse.app.AppContainer): Map<Int, com.cineverse.app.data.model.ShowProgress> {
    val library = kotlinx.coroutines.withTimeoutOrNull(10_000) { container.library.library.first { it.loaded } } ?: return emptyMap()
    return container.privacy.shows(container.episodes.progress.first(), library)
}

/** Redraw the widgets that name shows - a list just got a PIN. */
suspend fun refreshShowWidgets(context: Context) {
    runCatching { UpNextWidget().updateAllSafe(context) }
    runCatching { NextEpisodeWidget().updateAll(context) }
    runCatching { ShowCountdownWidget().updateAll(context) }
}

/** "Tonight, 21:00", "Tomorrow", "Thursday", "in 12 days". */
private fun whenLabel(item: UpNextItem): String {
    val zone = ZoneId.systemDefault()
    val at = Instant.ofEpochMilli(item.at).atZone(zone)
    val days = ChronoUnit.DAYS.between(LocalDate.now(zone), at.toLocalDate())
    val time = if (item.exact) ", " + at.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())) else ""
    return when {
        item.at <= System.currentTimeMillis() -> "Out now"
        days == 0L -> "Today$time"
        days == 1L -> "Tomorrow$time"
        days < 7 -> at.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())) + time
        else -> "in $days days"
    }
}

class NextEpisodeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextEpisodeWidget()
}
