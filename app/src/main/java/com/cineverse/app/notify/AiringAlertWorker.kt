package com.cineverse.app.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cineverse.app.CineVerseApp
import com.cineverse.app.data.airing.UpNextItem
import java.util.concurrent.TimeUnit

/**
 * "It's out": one alarm per episode, set for the minute the broadcaster says
 * it airs, on shows you are caught up on.
 *
 * Only where the time is exact. A date alone would mean midnight, and an
 * episode announced hours before anyone can watch it is worse than one
 * announced at breakfast by the sweep. The key is the sweep's own, so an
 * episode is announced once, whichever gets there first.
 */
class AiringAlertWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = (applicationContext as? CineVerseApp)?.container ?: return Result.success()
        val settings = app.settings.settings.value
        if (!settings.notifyNewEpisodes || !settings.notifyAiring) return Result.success()
        if (!Notifications.canPost(applicationContext)) return Result.success()

        val showId = inputData.getInt(SHOW, 0)
        val season = inputData.getInt(SEASON, 0)
        val episode = inputData.getInt(EPISODE, 0)
        if (showId <= 0) return Result.success()
        // Watched already, somewhere else, before it was even announced.
        if (app.episodes.of(showId)?.isWatched(season, episode) == true) return Result.success()

        val key = WatchWorker.episodeKey(showId, season, episode)
        val seen = app.settings.notifiedKeys.value.toMutableSet()
        if (!seen.add(key)) return Result.success()
        app.settings.rememberNotified(seen)

        val label = "S$season E$episode"
        val name = inputData.getString(NAME).orEmpty()
        Notifications.post(
            context = applicationContext,
            id = Notifications.idFor(key),
            channel = Notifications.CHANNEL_EPISODES,
            title = inputData.getString(TITLE).orEmpty(),
            body = buildString {
                append(label)
                if (name.isNotBlank() && !name.matches(Regex("Episode \\d+"))) append(" · $name")
                append(" is out now")
            },
            deepLink = "cineverse://tv/$showId",
            markWatched = MarkTarget(showId, season, episode, label),
            imageUrl = inputData.getString(ART),
        )
        return Result.success()
    }

    companion object {
        private const val SHOW = "show"
        private const val SEASON = "season"
        private const val EPISODE = "episode"
        private const val TITLE = "title"
        private const val NAME = "name"
        private const val ART = "art"

        /** Alarms for every exact time in the next day and a half; the rest wait for a sweep. */
        fun schedule(context: Context, items: List<UpNextItem>) {
            val now = System.currentTimeMillis()
            val manager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
            for (item in items) {
                val wait = item.at - now
                if (!item.exact || wait <= 0 || wait > 36 * 3_600_000L) continue
                val request = OneTimeWorkRequestBuilder<AiringAlertWorker>()
                    // A minute after the stated time, so the episode is there
                    // when the notification is tapped.
                    .setInitialDelay(wait + 60_000, TimeUnit.MILLISECONDS)
                    .setInputData(
                        workDataOf(
                            SHOW to item.show.id,
                            SEASON to item.next.season,
                            EPISODE to item.next.episode,
                            TITLE to item.show.name,
                            NAME to item.next.name,
                            ART to (com.cineverse.app.core.ui.Img.still(item.next.still)
                                ?: com.cineverse.app.core.ui.Img.poster(item.show.poster)),
                        )
                    )
                    .build()
                // REPLACE: a time that moved since the last sweep moves the alarm.
                manager.enqueueUniqueWork(
                    "airing_${item.show.id}_${item.next.season}_${item.next.episode}",
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            }
        }
    }
}
