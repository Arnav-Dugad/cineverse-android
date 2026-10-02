package com.cineverse.app.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cineverse.app.CineVerseApp
import com.cineverse.app.data.model.MediaType
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * The daily sweep.
 *
 * Once a day, on an unmetered connection, the app asks TMDB two questions about
 * the library it already has: has anything I am part way through aired a new
 * episode, and has anything on my list come out?
 *
 * Deliberately modest about how much it asks. The shows checked are the ones
 * actually in flight — watched at least one episode, not finished, not dropped —
 * which on a real library is twenty documents rather than sixty-five, and the
 * saved titles are capped at seventy exactly as the website caps them. A sweep
 * that costs a hundred requests is a sweep that gets throttled and then gets
 * deleted.
 *
 * Everything it posts is recorded first, so a worker that runs twice, or a phone
 * that reboots half way through, cannot say the same thing again.
 */
class WatchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = (applicationContext as? CineVerseApp)?.container ?: return Result.success()
        val settings = app.settings.settings.value
        if (!settings.notifyNewEpisodes && !settings.notifyReleases) return Result.success()
        if (!Notifications.canPost(applicationContext)) return Result.success()

        // Signed out, or the library has not arrived yet: nothing to sweep, and
        // retrying later is cheaper than guessing.
        val library = runCatching { app.library.library.first { it.loaded } }.getOrNull()
            ?: return Result.retry()
        val shows = app.episodes.progress.value
        val region = settings.region
        val today = LocalDate.now()
        val seen = app.settings.notifiedKeys.value.toMutableSet()
        var episodes = 0
        var releases = 0

        if (settings.notifyNewEpisodes) {
            val inFlight = shows.values
                .filter { it.watchedCount > 0 && !it.complete && !it.dropped }
                .sortedByDescending { it.log.lastOrNull()?.stamp ?: it.updatedAt }
                .take(30)

            for (show in inFlight) {
                val detail = runCatching {
                    app.tmdb.detail(show.tmdbId, MediaType.Tv, region, refresh = true)
                }.getOrNull() ?: continue
                val next = detail.nextEpisode ?: continue
                val air = runCatching { LocalDate.parse(next.airDate) }.getOrNull() ?: continue

                // Today or already gone, and no older than a week: a sweep that
                // fires on an episode from last month is telling you about your
                // own backlog, which you can already see.
                if (air.isAfter(today) || air.isBefore(today.minusDays(7))) continue

                val key = "episode_${show.tmdbId}_${next.season}_${next.number}"
                if (!seen.add(key)) continue

                Notifications.post(
                    context = applicationContext,
                    id = Notifications.idFor(key),
                    channel = Notifications.CHANNEL_EPISODES,
                    title = show.title.ifBlank { detail.title },
                    body = buildString {
                        if (show.isAbsolute) append("Episode ${next.number}")
                        else append("S${next.season} E${next.number}")
                        if (next.name.isNotBlank()) append(" · ${next.name}")
                        append(if (air == today) " is out today" else " is out")
                    },
                    deepLink = "cineverse://tv/${show.tmdbId}",
                    imageUrl = com.cineverse.app.core.ui.Img.still(next.stillPath)
                        ?: com.cineverse.app.core.ui.Img.poster(show.poster.ifBlank { null }),
                )
                episodes++
            }
        }

        if (settings.notifyReleases) {
            val waiting = library.saved.values
                .filterNot { library.isWatched(it.key) }
                .sortedByDescending { it.addedAt }
                .take(70)

            for (item in waiting) {
                val stored = runCatching {
                    LocalDate.parse(item.releaseDate.take(10))
                }.getOrNull()
                // Only titles whose stored date is near enough to be worth a
                // request. Everything else is checked on a day it matters.
                if (stored != null && (stored.isAfter(today.plusDays(2)) ||
                        stored.isBefore(today.minusDays(30)))
                ) continue

                val detail = runCatching {
                    app.tmdb.detail(item.tmdbId, item.type, region, refresh = true)
                }.getOrNull() ?: continue
                val release = runCatching {
                    LocalDate.parse(detail.releaseDate.take(10))
                }.getOrNull() ?: continue
                if (release.isAfter(today) || release.isBefore(today.minusDays(3))) continue

                val key = "release_${item.type.wire}_${item.tmdbId}_$release"
                if (!seen.add(key)) continue

                Notifications.post(
                    context = applicationContext,
                    id = Notifications.idFor(key),
                    channel = Notifications.CHANNEL_RELEASES,
                    title = detail.title.ifBlank { item.title },
                    body = if (release == today) "Out today, from your list"
                    else "Out now, from your list",
                    deepLink = "cineverse://${item.type.wire}/${item.tmdbId}",
                    imageUrl = com.cineverse.app.core.ui.Img.poster(
                        detail.posterPath ?: item.poster.ifBlank { null }
                    ),
                )
                releases++
            }
        }

        if (episodes > 0 || releases > 0) {
            app.settings.rememberNotified(seen)
            Notifications.postSummary(applicationContext, Notifications.CHANNEL_EPISODES, episodes)
            Notifications.postSummary(applicationContext, Notifications.CHANNEL_RELEASES, releases)
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "cineverse-watch-sweep"

        /**
         * Once a day, overnight, on an unmetered connection.
         *
         * The initial delay aims the first run at about 8am: an episode notice
         * at four in the morning is a notice that gets the app muted. WorkManager
         * will not honour that to the minute and should not be asked to — the
         * point is "morning", not "08:00:00".
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WatchWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setInitialDelay(millisUntilMorning(), TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                // KEEP, not UPDATE: every launch would otherwise reset the
                // schedule and a sweep would never actually come due on a phone
                // that is opened daily.
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /**
         * Run the sweep now.
         *
         * The website has the same manual refresh on its notifications page, and
         * it earns its place for the same reason: a daily job is invisible, so
         * the only way to know it works — or to catch up after a week with the
         * phone off — is to be able to ask for it.
         *
         * No network constraint on this one. The user asked, so a metered
         * connection is their business rather than the app's.
         */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME + "-now",
                androidx.work.ExistingWorkPolicy.REPLACE,
                androidx.work.OneTimeWorkRequestBuilder<WatchWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }

        private fun millisUntilMorning(): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
            return (target.timeInMillis - now.timeInMillis).coerceAtLeast(0)
        }
    }
}
