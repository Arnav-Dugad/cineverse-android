package com.cineverse.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cineverse.app.CineVerseApp
import com.cineverse.app.MainActivity
import com.cineverse.app.R
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** A film being watched right now, and since when. */
data class Watching(
    val id: Int,
    val title: String,
    val runtime: Int,
    /** When minute zero was, so a film started part way in counts from there. */
    val startedAt: Long,
    val poster: String,
) {
    fun elapsed(now: Long = System.currentTimeMillis()): Int =
        (((now - startedAt) / 60_000L).toInt()).coerceIn(0, runtime)

    val endsAt: Long get() = startedAt + runtime * 60_000L
}

/**
 * "Watching now": an Android 16 Live Update for the film on your screen.
 *
 * A progress bar the length of the film, a countdown to the credits in the
 * status bar chip, and two buttons - Finished, which marks it watched, and
 * Stop, which keeps your place for next time. Where the phone has no Live
 * Updates it is the same notification, ongoing, in the shade.
 *
 * The countdown is the system's own chronometer, so the minutes tick with the
 * app asleep; the bar is redrawn every five minutes.
 */
class WatchingNowRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("watching_now", Context.MODE_PRIVATE)
    private val _current = MutableStateFlow(read())
    val current: StateFlow<Watching?> = _current.asStateFlow()

    private fun read(): Watching? {
        val id = prefs.getInt("id", 0)
        if (id <= 0) return null
        val watching = Watching(
            id = id,
            title = prefs.getString("title", "").orEmpty(),
            runtime = prefs.getInt("runtime", 0),
            startedAt = prefs.getLong("startedAt", 0L),
            poster = prefs.getString("poster", "").orEmpty(),
        )
        // A film whose credits have long since rolled is not still on.
        return watching.takeIf { System.currentTimeMillis() < it.endsAt + 3 * 3_600_000L }
    }

    fun start(id: Int, title: String, runtime: Int, fromMinute: Int, poster: String) {
        val watching = Watching(
            id = id,
            title = title,
            runtime = runtime,
            startedAt = System.currentTimeMillis() - fromMinute.coerceIn(0, runtime) * 60_000L,
            poster = poster,
        )
        prefs.edit()
            .putInt("id", id).putString("title", title).putInt("runtime", runtime)
            .putLong("startedAt", watching.startedAt).putString("poster", poster)
            .apply()
        _current.value = watching
        show(context, watching)
        WatchingNowWorker.next(context)
    }

    /** Stop, and hand back where it got to. */
    fun stop(): Watching? {
        val was = _current.value
        prefs.edit().clear().apply()
        _current.value = null
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        WorkManager.getInstance(context).cancelUniqueWork(WatchingNowWorker.NAME)
        return was
    }

    companion object {
        const val CHANNEL = "watching_now"
        const val NOTIFICATION_ID = 7_001
        const val ACTION_FINISH = "com.cineverse.app.WATCHING_FINISH"
        const val ACTION_STOP = "com.cineverse.app.WATCHING_STOP"

        fun ensureChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL, "Watching now", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "The film you are watching, counting down to the credits."
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }

        fun show(context: Context, watching: Watching) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) return
            ensureChannel(context)
            val now = System.currentTimeMillis()
            val elapsed = watching.elapsed(now)
            val left = (watching.runtime - elapsed).coerceAtLeast(0)
            val open = PendingIntent.getActivity(
                context, NOTIFICATION_ID,
                Intent(Intent.ACTION_VIEW, Uri.parse("cineverse://movie/${watching.id}"), context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            fun action(name: String, code: Int) = PendingIntent.getBroadcast(
                context, code,
                Intent(context, WatchingNowReceiver::class.java).setAction(name),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val style = NotificationCompat.ProgressStyle()
                .setStyledByProgress(true)
                // CineVerse red for the film so far, so the bar reads as progress.
                .setProgressSegments(
                    listOf(NotificationCompat.ProgressStyle.Segment(watching.runtime.coerceAtLeast(1)).setColor(0xFFFF2030.toInt()))
                )
                .setProgress(elapsed)
            val builder = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(watching.title)
                .setContentText(if (left > 0) "${duration(left)} to the credits" else "The credits should be rolling")
                .setStyle(style)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(open)
                // The status bar chip: the time left, as short as it can be.
                .setShortCriticalText(if (left >= 60) "${left / 60}h${left % 60}m" else "${left}m")
                .setRequestPromotedOngoing(true)
                .setWhen(watching.endsAt)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .addAction(R.drawable.ic_notification, "Finished", action(ACTION_FINISH, NOTIFICATION_ID + 1))
                .addAction(R.drawable.ic_notification, "Stop", action(ACTION_STOP, NOTIFICATION_ID + 2))
            runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build()) }
        }

        private fun duration(minutes: Int) = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
    }
}

/** Redraws the bar every five minutes, and lets go when the film ends. */
class WatchingNowWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = (applicationContext as? CineVerseApp)?.container ?: return Result.success()
        val watching = app.watchingNow.current.value ?: return Result.success()
        WatchingNowRepository.show(applicationContext, watching)
        if (System.currentTimeMillis() < watching.endsAt + 20 * 60_000L) next(applicationContext)
        return Result.success()
    }

    companion object {
        const val NAME = "watching_now_refresh"
        fun next(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WatchingNowWorker>().setInitialDelay(5, TimeUnit.MINUTES).build(),
            )
        }
    }
}

/** Finished marks the film watched; Stop keeps your place. Both end the Live Update. */
class WatchingNowReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as? CineVerseApp)?.container ?: return
        val pending = goAsync()
        container.scope.launch {
            try {
                val was = container.watchingNow.stop() ?: return@launch
                val item = MediaItem(was.id, MediaType.Movie, was.title, posterPath = was.poster.ifBlank { null })
                when (intent.action) {
                    WatchingNowRepository.ACTION_FINISH -> runCatching {
                        if (!container.library.library.value.isWatched(item.key)) container.library.toggleWatched(item)
                        // Finished: out of Continue Watching, as the website does it.
                        container.library.clearMovieProgress(was.id)
                    }
                    WatchingNowRepository.ACTION_STOP -> runCatching {
                        val at = was.elapsed()
                        if (at > 0) container.library.setMovieProgress(was.id, at, was.runtime)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
