package com.cineverse.app.core.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.cineverse.app.core.design.CvTheme

/**
 * TMDB's image sizes, picked rather than guessed.
 *
 * Asking for `original` everywhere is the single easiest way to make a media app
 * feel slow: a 2000px backdrop behind a 48px avatar costs the same bytes as the
 * backdrop. Each constant below is the smallest TMDB size that is still sharp at
 * the place it is used on a 3x screen.
 */
object Img {
    private const val BASE = "https://image.tmdb.org/t/p/"

    /** A poster in a rail (≈150dp wide). */
    fun poster(path: String?): String? = path?.let { "${BASE}w500$it" }

    /** The big poster on a title page. */
    fun posterLarge(path: String?): String? = path?.let { "${BASE}w780$it" }

    /** The blurred placeholder behind one, two orders of magnitude smaller. */
    fun tiny(path: String?): String? = path?.let { "${BASE}w92$it" }

    /** Full-bleed artwork behind a hero or a title page. */
    fun backdrop(path: String?): String? = path?.let { "${BASE}w1280$it" }

    /** A Continue Watching card or an episode still. */
    fun still(path: String?): String? = path?.let { "${BASE}w500$it" }

    /** A face. */
    fun profile(path: String?): String? = path?.let { "${BASE}w185$it" }

    /** A title logo, which is drawn at its natural width. */
    fun logo(path: String?): String? = path?.let { "${BASE}w500$it" }

    /** A streaming service's mark. */
    fun provider(path: String?): String? = path?.let { "${BASE}w92$it" }
}

/**
 * One image composable for the whole app, so every piece of artwork fades in the
 * same way and nothing ever pops.
 */
@Composable
fun CvImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val colors = CvTheme.colors
    Box(modifier.background(colors.surface2)) {
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(url)
                    .crossfade(220)
                    .build(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The sweep that stands in for content while it loads.
 *
 * It travels the same way on every surface in the app and never loops faster
 * than once every 1.4 seconds — a shimmer that races reads as the app being
 * frantic rather than busy.
 */
@Composable
fun Modifier.shimmer(active: Boolean = true): Modifier {
    if (!active || CvTheme.reducedMotion) {
        return this.background(CvTheme.colors.surface2)
    }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )
    val base = CvTheme.colors.surface2
    val highlight = if (CvTheme.colors.isDark) Color.White.copy(alpha = 0.05f)
    else Color.Black.copy(alpha = 0.04f)
    return this.background(
        Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = androidx.compose.ui.geometry.Offset(progress * 1400f - 700f, 0f),
            end = androidx.compose.ui.geometry.Offset(progress * 1400f, 400f),
        )
    )
}
