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
 * The sweep, twice a day, morning and evening.
 *
 * It asks TMDB about the library the app already has, and says only what is
 * new, each kind behind its own switch in Settings:
 *
 *  - **New episodes** on a show you are watching. Read from TMDB's LAST aired
 *    episode as well as its next one: TMDB moves "next to air" on to the
 *    following week the moment an episode airs, so a sweep that read only
 *    that pointer missed almost every episode it was meant to announce.
 *  - **The minute it airs**, where the broadcaster publishes a time: a
 *    one-off alarm is set for each episode due in the next day and a half.
 *  - **New seasons** of shows you have watched.
 *  - **Releases** from your list.
 *  - **Your month**, on the first.
 *  - **App updates.**
 *
 * Everything it posts is recorded first, so a sweep that runs twice, or a
 * phone that reboots half way through, cannot say the same thing again.
 */
class WatchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = (applicationContext as? CineVerseApp)?.container ?: return Result.success()
        val settings = app.settings.settings.value
        if (!Notifications.canPost(applicationContext)) return Result.success()
        val seen = app.settings.notifiedKeys.value.toMutableSet()
        val counts = mutableMapOf<String, Int>()
        fun posted(channel: String) { counts[channel] = (counts[channel] ?: 0) + 1 }

        // An update needs no account.
        if (settings.notifyUpdates) {
            val state = runCatching { app.updates.check() }.getOrNull()
            if (state is com.cineverse.app.update.UpdateState.Available) {
                val key = "update_${state.release.versionCode}"
                if (seen.add(key)) {
                    Notifications.post(
                        context = applicationContext,
                        id = Notifications.idFor(key),
                        channel = Notifications.CHANNEL_UPDATES,
                        title = "CineVerse ${state.release.versionName} is ready",
                        body = "Tap to see what's new and install it.",
                        deepLink = "cineverse://update",
                        imageUrl = null,
                    )
                }
            }
        }

        // Signed out, or the library has not arrived yet: nothing to sweep, and
        // retrying later is cheaper than guessing.
        val signedIn = app.auth.uid.value != null
        val library = if (!signedIn) null
        else kotlinx.coroutines.withTimeoutOrNull(20_000) { app.library.library.first { it.loaded } }
        if (library == null) {
            app.settings.rememberNotified(seen)
            return if (signedIn) Result.retry() else Result.success()
        }
        val shows = app.episodes.progress.value
        val region = settings.region
        val today = LocalDate.now()

        if (settings.notifyNewEpisodes) {
            val inFlight = shows.values
                .filter { it.watchedCount > 0 && !it.complete && !it.dropped }
                .sortedByDescending { it.log.lastOrNull()?.stamp ?: it.updatedAt }
                .take(30)

            var hooks = 0
            for (show in inFlight) {
                val detail = runCatching {
                    app.tmdb.detail(show.tmdbId, MediaType.Tv, region, refresh = true)
                }.getOrNull() ?: continue
                val candidates = listOfNotNull(detail.lastEpisode, detail.nextEpisode)
                    .distinctBy { it.season to it.number }
                for (episode in candidates) {
                    val air = runCatching { LocalDate.parse(episode.airDate) }.getOrNull() ?: continue
                    // Out, and no older than a week: a notice about an episode
                    // from last month is telling you about your own backlog.
                    if (air.isAfter(today) || air.isBefore(today.minusDays(7))) continue
                    if (show.isWatched(episode.season, episode.number)) continue
                    val key = episodeKey(show.tmdbId, episode.season, episode.number)
                    if (!seen.add(key)) continue
                    val label = if (show.isAbsolute) "Episode ${episode.number}" else "S${episode.season} E${episode.number}"
                    // Gemini's one-line hook, from the episode you saw last.
                    // A handful per run at most: the free tier has a daily limit.
                    val hook = if (settings.geminiOn && hooks < 4) {
                        hooks++
                        runCatching { app.episodeHook.line(show, episode) }.getOrNull()
                    } else null
                    Notifications.post(
                        context = applicationContext,
                        id = Notifications.idFor(key),
                        channel = Notifications.CHANNEL_EPISODES,
                        title = show.title.ifBlank { detail.title },
                        body = hook?.let { "$label: $it" } ?: buildString {
                            append(label)
                            if (episode.name.isNotBlank() && !GenericName.matches(episode.name)) append(" · ${episode.name}")
                            append(if (air == today) " is out today" else " is out")
                        },
                        deepLink = "cineverse://tv/${show.tmdbId}",
                        markWatched = MarkTarget(show.tmdbId, episode.season, episode.number, label),
                        imageUrl = com.cineverse.app.core.ui.Img.still(episode.stillPath)
                            ?: com.cineverse.app.core.ui.Img.poster(show.poster.ifBlank { null }),
                    )
                    posted(Notifications.CHANNEL_EPISODES)
                }
            }
        }

        // The minute it airs: alarms for the episodes due soon on shows you
        // are caught up on, where the broadcaster's time is known.
        if (settings.notifyNewEpisodes && settings.notifyAiring) {
            val upNext = runCatching { app.airing.upNext(shows) }.getOrDefault(emptyList())
            AiringAlertWorker.schedule(applicationContext, upNext)
        }

        if (settings.notifySeasons) {
            val watchedShows = library.watched.values.filter { it.type == MediaType.Tv }.map { it.tmdbId }
            val returning = runCatching { app.airing.returning(shows, watchedShows) }.getOrDefault(emptyList())
            for (entry in returning) {
                val day = runCatching { LocalDate.parse(entry.airDate) }.getOrNull() ?: continue
                val soon = day == today.plusDays(1)
                val out = !day.isAfter(today) && !day.isBefore(today.minusDays(3))
                if (!soon && !out) continue
                val key = "season_${entry.show.id}_${entry.season}_${if (out) "out" else "soon"}"
                if (!seen.add(key)) continue
                Notifications.post(
                    context = applicationContext,
                    id = Notifications.idFor(key),
                    channel = Notifications.CHANNEL_SEASONS,
                    title = entry.show.name,
                    body = if (out) "Season ${entry.season} is out" else "Season ${entry.season} starts tomorrow",
                    deepLink = "cineverse://tv/${entry.show.id}",
                    imageUrl = com.cineverse.app.core.ui.Img.still(entry.show.backdrop)
                        ?: com.cineverse.app.core.ui.Img.poster(entry.show.poster),
                )
                posted(Notifications.CHANNEL_SEASONS)
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
                posted(Notifications.CHANNEL_RELEASES)
            }
        }

        // The first three days of a month, once.
        if (settings.notifyRecap && today.dayOfMonth <= 3) {
            com.cineverse.app.data.inbox.InboxRepository.monthlyRecap(library, shows, today)?.let { recap ->
                val key = "recap_${recap.id}"
                if (seen.add(key)) {
                    Notifications.post(
                        context = applicationContext,
                        id = Notifications.idFor(key),
                        channel = Notifications.CHANNEL_RECAP,
                        title = recap.title,
                        body = recap.body + ". See your year so far.",
                        deepLink = "cineverse://year",
                        imageUrl = null,
                    )
                }
            }
        }

        app.settings.rememberNotified(seen)
        for ((channel, count) in counts) Notifications.postSummary(applicationContext, channel, count)
        return Result.success()
    }

    companion object {
        private const val NAME = "cineverse-sweep-twice-daily"
        /** The once-a-day, Wi-Fi-only sweep of 1.5 and earlier, cancelled on upgrade. */
        private const val OLD_NAME = "cineverse-watch-sweep"

        /** "Episode 4" says nothing a number does not. */
        private val GenericName = Regex("Episode \\d+")

        /** A key shared with the airing alarms, so one episode is announced once. */
        fun episodeKey(showId: Int, season: Int, episode: Int) = "episode_${showId}_${season}_$episode"

        /**
         * Every twelve hours, the first run aimed at about 8am.
         *
         * This used to be once a day and ONLY on unmetered Wi-Fi with a good
         * battery, which on a phone that lives on mobile data meant it hardly
         * ran at all. Any connection now: a sweep is a few dozen small
         * requests. WorkManager will not honour the time to the minute and is
         * not asked to - "morning and evening" is the point.
         */
        fun schedule(context: Context) {
            val manager = WorkManager.getInstance(context)
            manager.cancelUniqueWork(OLD_NAME)
            val request = PeriodicWorkRequestBuilder<WatchWorker>(12, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInitialDelay(millisUntilMorning(), TimeUnit.MILLISECONDS)
                .build()

            manager.enqueueUniquePeriodicWork(
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
