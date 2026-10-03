package com.cineverse.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.glance.appwidget.updateAll
import com.cineverse.app.CineVerseApp
import com.cineverse.app.R
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.model.TitleDetail
import kotlinx.coroutines.launch

/**
 * "Mark watched" on a new-episode notification: the episode is ticked
 * straight from the shade, without opening the app, through the same
 * repository - and the same merge rules - as every other tick.
 *
 * The notification then becomes a short confirmation and clears itself, so
 * the action visibly worked and leaves nothing behind.
 */
class MarkWatchedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val showId = intent.getIntExtra(EXTRA_SHOW, 0)
        val season = intent.getIntExtra(EXTRA_SEASON, 0)
        val episode = intent.getIntExtra(EXTRA_EPISODE, 0)
        val id = intent.getIntExtra(EXTRA_NOTIFICATION, 0)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        if (showId <= 0 || season <= 0 || episode <= 0) return

        val pending = goAsync()
        val container = (context.applicationContext as CineVerseApp).container
        container.scope.launch {
            try {
                val held = container.episodes.of(showId)
                val detail = container.tmdb.cachedDetail(showId, MediaType.Tv, container.settings.settings.value.region)
                    ?: TitleDetail(
                        id = showId,
                        type = MediaType.Tv,
                        title = held?.title?.ifBlank { null } ?: title,
                        posterPath = held?.poster?.ifBlank { null },
                        backdropPath = held?.backdrop?.ifBlank { null },
                        episodeRuntime = held?.episodeRuntime ?: 0,
                    )
                val done = runCatching { container.episodes.markEpisode(detail, season, episode) }.isSuccess
                confirm(context, id, title, if (done) "$label marked watched" else "Could not mark $label. Open CineVerse to try again.")
                runCatching {
                    com.cineverse.app.widget.ContinueWidget().updateAll(context)
                    com.cineverse.app.widget.TodayWidget().updateAll(context)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun confirm(context: Context, id: Int, title: String, text: String) {
        if (!Notifications.canPost(context)) return
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_EPISODES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(true)
            .setTimeoutAfter(4_000)
            .setGroup(Notifications.CHANNEL_EPISODES)
            .build()
        runCatching {
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }

    companion object {
        const val ACTION = "com.cineverse.app.MARK_WATCHED"
        const val EXTRA_SHOW = "showId"
        const val EXTRA_SEASON = "season"
        const val EXTRA_EPISODE = "episode"
        const val EXTRA_NOTIFICATION = "notificationId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_LABEL = "label"
    }
}
