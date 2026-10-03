package com.cineverse.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
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
import com.cineverse.app.CineVerseApp
import com.cineverse.app.MainActivity
import com.cineverse.app.core.design.Palette
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.model.TitleDetail
import kotlinx.coroutines.async
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

    // Exact, so a 4x2 and a 4x4 show different numbers of rows rather than one
    // layout being stretched into a shape it was never drawn for.
    override val sizeMode = androidx.glance.appwidget.SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as CineVerseApp).container
        // Wait for the first real emission rather than drawing an empty widget
        // and filling it a moment later.
        val shows = container.episodes.progress.first()
        val rows = shows.values
            .filter { it.watchedCount > 0 && !it.dropped && it.nextUp() != null }
            .sortedByDescending { it.log.lastOrNull()?.stamp ?: it.updatedAt }
            .take(6)
            .map { show ->
                val next = show.nextUp()!!
                WidgetRow(
                    showId = show.tmdbId,
                    title = show.title,
                    poster = show.poster,
                    season = next.first,
                    episode = next.second,
                    absolute = show.isAbsolute,
                    watched = show.watchedCount,
                    total = show.totalEpisodes,
                    remaining = show.airedRemaining,
                )
            }

        // Every poster at once rather than one after another: six sequential
        // loads is six round trips of latency on a widget the launcher expects
        // to have drawn already.
        val art: Map<Int, android.graphics.Bitmap> = kotlinx.coroutines.coroutineScope {
            rows
                .map { row ->
                    this@coroutineScope.async {
                        row.showId to WidgetArt.poster(context, row.poster.ifBlank { null })
                    }
                }
                .mapNotNull { pending ->
                    val (id, bitmap) = pending.await()
                    bitmap?.let { id to it }
                }
                .toMap()
        }

        provideContent {
            GlanceTheme {
                WidgetBody(context, rows, art)
            }
        }
    }

    @Composable
    private fun WidgetBody(
        context: Context,
        rows: List<WidgetRow>,
        art: Map<Int, android.graphics.Bitmap>,
    ) {
        Column(
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(28.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                GlanceModifier.fillMaxWidth().clickable(openApp(context)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Continue watching",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (rows.isNotEmpty()) {
                    Text(
                        rows.size.toString(),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 12.sp,
                        ),
                    )
                }
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
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // The artwork is what makes a widget recognisable at a
                        // glance, which is the only glance a widget gets. The
                        // first version was a column of names and lost the home
                        // screen to a weather tile.
                        Box(
                            GlanceModifier
                                .size(width = 42.dp, height = 63.dp)
                                .cornerRadius(10.dp)
                                .background(GlanceTheme.colors.surfaceVariant)
                                .clickable(openTitle(context, row.showId)),
                            contentAlignment = Alignment.Center,
                        ) {
                            art[row.showId]?.let { poster ->
                                Image(
                                    provider = ImageProvider(poster),
                                    contentDescription = row.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = GlanceModifier.fillMaxSize(),
                                )
                            }
                        }
                        Spacer(GlanceModifier.width(11.dp))

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
                            Spacer(GlanceModifier.height(2.dp))
                            Text(
                                row.label,
                                maxLines = 1,
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = 11.sp,
                                ),
                            )
                            if (row.total > 0) {
                                Spacer(GlanceModifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = (row.watched.toFloat() / row.total)
                                        .coerceIn(0f, 1f),
                                    color = androidx.glance.unit.ColorProvider(Palette.Red2),
                                    backgroundColor = GlanceTheme.colors.surfaceVariant,
                                    modifier = GlanceModifier.fillMaxWidth().height(3.dp),
                                )
                            }
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
                                "\u2713",
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
    val poster: String,
    val season: Int,
    val episode: Int,
    val absolute: Boolean,
    val watched: Int,
    val total: Int,
    val remaining: Int,
) {
    /**
     * What is next, and how much is left.
     *
     * An absolute-numbered show reads "EP 1107", never "S22 E1107". The season
     * is an artefact of how TMDB files One Piece, not something anyone watching
     * it keeps track of.
     */
    val label: String
        get() = buildString {
            if (absolute) append("EP ").append(episode)
            else append("S").append(season).append(" E").append(episode)
            if (remaining > 0) append("  \u00b7  ").append(remaining).append(" left")
        }
}

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
        // Both tiles: the streak and the figures for today have changed too.
        ContinueWidget().updateAll(context)
        TodayWidget().updateAll(context)
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
