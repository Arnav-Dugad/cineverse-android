package com.cineverse.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
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
import com.cineverse.app.CineVerseApp
import com.cineverse.app.MainActivity
import com.cineverse.app.core.design.Palette
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.model.TitleDetail
import kotlinx.coroutines.flow.first

/**
 * Continue Watching, on the home screen.
 *
 * This is the single biggest tap saving in the product: the tick on the widget
 * marks the next episode watched **without opening the app**. Someone who just
 * finished an episode on the television can tick it from their launcher in one
 * tap, which is three fewer than the website can ever manage.
 *
 * It reads the same Firestore documents the app does. Firestore's offline cache
 * means that is a local read, so the widget draws immediately rather than
 * waiting on a network.
 */
class ContinueWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as CineVerseApp).container
        // Wait for the first real emission rather than drawing an empty widget
        // and filling it a moment later.
        val shows = container.episodes.progress.first()
        val rows = shows.values
            .filter { it.watchedCount > 0 && !it.dropped && it.nextUp() != null }
            .sortedByDescending { it.log.lastOrNull()?.stamp ?: it.updatedAt }
            .take(8)
            .map { show ->
                val next = show.nextUp()!!
                WidgetRow(
                    showId = show.tmdbId,
                    title = show.title,
                    season = next.first,
                    episode = next.second,
                    remaining = (show.totalEpisodes - show.watchedCount).coerceAtLeast(0),
                )
            }

        provideContent {
            GlanceTheme {
                WidgetBody(context, rows)
            }
        }
    }

    @Composable
    private fun WidgetBody(context: Context, rows: List<WidgetRow>) {
        Column(
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(24.dp)
                .padding(14.dp)
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Continue watching",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = GlanceModifier.clickable(openApp(context)),
                )
            }
            Spacer(GlanceModifier.height(10.dp))

            if (rows.isEmpty()) {
                Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nothing on the go.\nTick an episode and it turns up here.",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 12.sp,
                        ),
                    )
                }
                return@Column
            }

            LazyColumn {
                items(rows, itemId = { it.showId.toLong() }) { row ->
                    Row(
                        GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            GlanceModifier
                                .defaultWeight()
                                .clickable(openTitle(context, row.showId)),
                        ) {
                            Text(
                                row.title,
                                maxLines = 1,
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurface,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                            Text(
                                "S${row.season} E${row.episode}" +
                                    if (row.remaining > 0) "  ·  ${row.remaining} left" else "",
                                maxLines = 1,
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = 11.sp,
                                ),
                            )
                        }
                        Spacer(GlanceModifier.width(10.dp))
                        // The whole point of the widget.
                        Box(
                            GlanceModifier
                                .size(40.dp)
                                .cornerRadius(20.dp)
                                .background(GlanceTheme.colors.primary)
                                .clickable(
                                    actionRunCallback<TickAction>(
                                        actionParametersOf(
                                            TickAction.showIdKey to row.showId,
                                            TickAction.seasonKey to row.season,
                                            TickAction.episodeKey to row.episode,
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "✓",
                                style = TextStyle(
                                    color = GlanceTheme.colors.onPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun openApp(context: Context) = actionStartActivity(
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

    /** Opening one row goes straight to that show's page. */
    private fun openTitle(context: Context, showId: Int) = actionStartActivity(
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(android.net.Uri.parse("cineverse://tv/$showId"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private data class WidgetRow(
    val showId: Int,
    val title: String,
    val season: Int,
    val episode: Int,
    val remaining: Int,
)

private val Int.sp get() = androidx.compose.ui.unit.TextUnit(
    this.toFloat(),
    androidx.compose.ui.unit.TextUnitType.Sp,
)

/**
 * Ticking from the widget.
 *
 * It writes through the same repository the app uses, so the merge rules, the
 * log encoding and the offline queue are all identical — there is no second
 * code path that could disagree with the first.
 */
class TickAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val container = (context.applicationContext as CineVerseApp).container
        val showId = parameters[showIdKey] ?: return
        val season = parameters[seasonKey] ?: return
        val episode = parameters[episodeKey] ?: return

        // The repository needs a title to stamp the document's metadata with.
        // The cached detail is almost always there; when it is not, the progress
        // document already carries everything that matters.
        val held = container.episodes.of(showId)
        val detail = container.tmdb.cachedDetail(showId, MediaType.Tv, container.settings.settings.value.region)
            ?: TitleDetail(
                id = showId,
                type = MediaType.Tv,
                title = held?.title.orEmpty(),
                posterPath = held?.poster?.ifBlank { null },
                backdropPath = held?.backdrop?.ifBlank { null },
                episodeRuntime = held?.episodeRuntime ?: 0,
            )
        container.episodes.toggleEpisode(detail, season, episode)
        ContinueWidget().updateAll(context)
    }

    companion object {
        val showIdKey = ActionParameters.Key<Int>("showId")
        val seasonKey = ActionParameters.Key<Int>("season")
        val episodeKey = ActionParameters.Key<Int>("episode")
    }
}

class ContinueWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ContinueWidget()
}
