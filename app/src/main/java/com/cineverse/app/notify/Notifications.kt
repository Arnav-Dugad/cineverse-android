package com.cineverse.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.cineverse.app.MainActivity
import com.cineverse.app.R

/**
 * Telling someone an episode has landed.
 *
 * The one thing a phone can do that the website cannot. The website has to be
 * open to tell you anything; a phone can say "One Piece 1107 is out" at seven in
 * the morning and be the reason you open the app at all.
 *
 * The rules this follows, all of which exist because the alternative is an app
 * people silence:
 *
 *  - **Two channels, separately controllable.** Episodes of shows you are part
 *    way through are urgent in a way that a film's release date is not, and
 *    Android lets someone keep one and mute the other — but only if they are
 *    not bundled into one channel called "CineVerse".
 *  - **Nothing is ever posted twice.** Every notification carries a stable key
 *    and the key is recorded before the post, so a worker that runs twice in a
 *    day, or a phone that reboots mid-sweep, cannot repeat itself.
 *  - **No sound by default on releases.** A film coming out next Tuesday does
 *    not deserve a chime.
 *  - **Tapping it opens the title**, not the app's front door.
 */
object Notifications {

    const val CHANNEL_EPISODES = "episodes"
    const val CHANNEL_RELEASES = "releases"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_EPISODES,
                "New episodes",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "A show you are watching has a new episode."
                enableLights(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RELEASES,
                "Releases",
                // Quietly: a release date is news, not an interruption.
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Something on your list is out."
            }
        )
    }

    fun canPost(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Post one.
     *
     * The artwork is fetched through the app's own Coil loader, so it comes out
     * of the same disk cache the rails filled and almost never costs a request.
     * A notification whose picture failed is posted anyway — the words are the
     * point.
     */
    suspend fun post(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        body: String,
        deepLink: String,
        imageUrl: String?,
    ) {
        if (!canPost(context)) return
        ensureChannels(context)

        val art = imageUrl?.let { url ->
            runCatching {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(url)
                    // A hardware bitmap cannot be handed to the notification
                    // shade, which draws it in another process.
                    .allowHardware(false)
                    .build()
                loader.execute(request).image?.toBitmap()
            }.getOrNull()
        }

        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(deepLink),
            context,
            MainActivity::class.java,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            // Grouped, so six episodes on a Friday are one stack rather than six
            // separate interruptions.
            .setGroup(channel)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)

        if (art != null) builder.setLargeIcon(art)

        runCatching {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        }
    }

    /** The one summary that holds a group together. */
    fun postSummary(context: Context, channel: String, count: Int) {
        if (!canPost(context) || count < 2) return
        val text = when (channel) {
            CHANNEL_EPISODES -> "$count new episodes"
            else -> "$count titles out now"
        }
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("CineVerse")
            .setContentText(text)
            .setGroup(channel)
            .setGroupSummary(true)
            .setAutoCancel(true)
        runCatching {
            NotificationManagerCompat.from(context).notify(channel.hashCode(), builder.build())
        }
    }

    /** A stable, collision-resistant id from a key the sweep already computes. */
    fun idFor(key: String): Int = key.hashCode() and 0x7FFFFFFF
}
