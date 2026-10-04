package com.cineverse.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap

/**
 * Artwork for a widget.
 *
 * Three things make this different from loading an image anywhere else in the
 * app, and all three are constraints of the widget host rather than choices:
 *
 *  - **It must be a Bitmap, and a software one.** A widget is drawn by the
 *    launcher, in another process; a hardware bitmap cannot cross that boundary
 *    and comes out blank.
 *  - **It must be small.** Everything a widget draws travels through a
 *    transaction with a hard size limit, and a handful of full-size posters
 *    will exceed it — the symptom is not an error but a widget that silently
 *    refuses to update.
 *  - **The corners have to be baked in.** Glance will round a Box, but an Image
 *    inside it keeps its own square corners on most launchers, so the rounding
 *    is done to the pixels.
 */
object WidgetArt {

    /** w185 posters, which is plenty at 48dp and a tenth of the bytes of w500. */
    suspend fun poster(context: Context, path: String?, radiusPx: Float = 18f): Bitmap? {
        if (path.isNullOrBlank()) return null
        val url = "https://image.tmdb.org/t/p/w185$path"
        return load(context, url, radiusPx)
    }

    /** w300 stills, for the wide row the big widget draws. */
    suspend fun still(context: Context, path: String?, radiusPx: Float = 18f): Bitmap? {
        if (path.isNullOrBlank()) return null
        return load(context, "https://image.tmdb.org/t/p/w300$path", radiusPx)
    }

    /** w780 backdrops, for the large Up Next widget's hero. */
    suspend fun backdrop(context: Context, path: String?, radiusPx: Float = 0f): Bitmap? {
        if (path.isNullOrBlank()) return null
        return load(context, "https://image.tmdb.org/t/p/w780$path", radiusPx)
    }

    private suspend fun load(context: Context, url: String, radiusPx: Float): Bitmap? =
        runCatching {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
            val bitmap = loader.execute(request).image?.toBitmap() ?: return null
            if (radiusPx <= 0f) bitmap else round(bitmap, radiusPx)
        }.getOrNull()

    private fun round(source: Bitmap, radius: Float): Bitmap {
        val output = createBitmap(source.width, source.height)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val bounds = RectF(0f, 0f, source.width.toFloat(), source.height.toFloat())
        // Scale the radius with the bitmap, so a w185 poster and a w300 still
        // come back with corners that look the same once they are laid out at
        // very different sizes.
        val scaled = radius * (source.width / 185f).coerceAtLeast(1f)
        canvas.drawRoundRect(bounds, scaled, scaled, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }
}
