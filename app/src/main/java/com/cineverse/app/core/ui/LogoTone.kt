package com.cineverse.app.core.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.cineverse.app.core.design.CvTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * What colour a title logo actually is.
 *
 * Official title treatments are almost all cut for dark backgrounds: white
 * lettering that vanishes the moment the app is in its light theme. The
 * website solves this by sampling each logo once and tagging it, and this is
 * the same algorithm with the same thresholds, so a logo that reads as white
 * on the laptop reads as white here.
 *
 * Earlier the app tinted every mark to one ink, which fixed the dark ones and
 * erased the white ones; then it plated them, which works but costs a chip
 * around every logo. Sampling is the honest answer: find out what the artwork
 * IS, and only change the ones that would actually disappear.
 */
enum class LogoTone {
    /** White or near-white lettering. Needs inverting on a light page. */
    Light,

    /** White type beside a colourful mark. Flattening it would blot the mark. */
    Mixed,

    /** Dark ink, drawn for paper. Needs inverting on a dark page. */
    Dark,

    /** Properly coloured artwork. Never touched. */
    Color,

    Unknown,
}

/**
 * The website's thresholds, unchanged.
 *
 * Pixels below 140 alpha are skipped, because a logo is mostly transparent and
 * averaging the empty space would call everything mid-grey.
 */
fun toneOfPixels(pixels: IntArray): LogoTone {
    var count = 0
    var luminance = 0.0
    var whites = 0
    var blacks = 0
    var chroma = 0.0

    for (pixel in pixels) {
        val a = (pixel ushr 24) and 0xFF
        if (a < 140) continue
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        val y = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
        val c = (max(r, max(g, b)) - min(r, min(g, b))) / 255.0
        count++
        luminance += y
        chroma += c
        if (y > 0.82 && c < 0.18) whites++
        if (y < 0.18) blacks++
    }
    if (count < 12) return LogoTone.Unknown

    val mean = luminance / count
    val whiteShare = whites.toDouble() / count
    val meanChroma = chroma / count

    if (whiteShare >= 0.5 || (mean > 0.66 && meanChroma < 0.2)) return LogoTone.Light
    // White lettering vanishes on paper even when a colourful mark carries most
    // of the logo. Flattening it to ink would blot the mark, so it keeps its
    // hues and has its lightness inverted instead.
    if (whiteShare >= 0.12) return if (meanChroma >= 0.12) LogoTone.Mixed else LogoTone.Light
    if (blacks.toDouble() / count >= 0.5 || mean < 0.22) return LogoTone.Dark
    return LogoTone.Color
}

/** Sampled tones, for the session. A logo does not change colour. */
private val toneCache = mutableMapOf<String, LogoTone>()

/**
 * A title treatment that reads on whichever page it lands on.
 *
 * Nothing is recoloured unless it would otherwise be invisible:
 *
 *  - a WHITE logo on the light theme becomes ink;
 *  - a DARK logo on the dark theme becomes white;
 *  - a MIXED logo has its lightness inverted but keeps its hues, so the type
 *    appears and the coloured mark survives;
 *  - a properly coloured logo is never touched, on either theme.
 *
 * The sample is 96px at most and happens once per URL, off the main thread.
 * Until it returns the logo is drawn exactly as it is, which is the right
 * default: most logos are fine, and a flash of correct artwork is better than
 * a flash of nothing.
 */
@Composable
fun TonedLogo(
    path: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    align: Alignment = Alignment.BottomStart,
) {
    val url = Img.logo(path) ?: return
    val context = LocalContext.current
    val dark = CvTheme.colors.isDark
    var tone by remember(url) { mutableStateOf(toneCache[url] ?: LogoTone.Unknown) }

    LaunchedEffect(url) {
        if (toneCache.containsKey(url)) return@LaunchedEffect
        val sampled = withContext(Dispatchers.Default) { sample(context, url) }
        toneCache[url] = sampled
        tone = sampled
    }

    CvImage(
        url,
        contentDescription,
        modifier,
        contentScale = ContentScale.Fit,
        background = Color.Transparent,
        colorFilter = filterFor(tone, dark),
        // Without this a short logo floated in the middle of its box, which
        // left "More like" a thumb's width away from the name it introduces.
        alignment = align,
    )
}

private fun filterFor(tone: LogoTone, dark: Boolean): ColorFilter? = when {
    tone == LogoTone.Light && !dark -> ColorFilter.tint(Color(0xFF0B0B10))
    tone == LogoTone.Dark && dark -> ColorFilter.tint(Color.White)
    tone == LogoTone.Mixed && !dark -> ColorFilter.colorMatrix(INVERT_LIGHTNESS)
    else -> null
}

/**
 * Inverts lightness while leaving hue alone.
 *
 * A straight negative would turn a blue mark orange. This flips each channel
 * about its own midpoint, which darkens white type to near-black and leaves a
 * saturated colour recognisably itself.
 */
private val INVERT_LIGHTNESS = ColorMatrix(
    floatArrayOf(
        -0.72f, 0f, 0f, 0f, 220f,
        0f, -0.72f, 0f, 0f, 220f,
        0f, 0f, -0.72f, 0f, 220f,
        0f, 0f, 0f, 1f, 0f,
    )
)

private suspend fun sample(context: android.content.Context, url: String): LogoTone =
    runCatching {
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(url)
            // A hardware bitmap cannot have its pixels read back.
            .allowHardware(false)
            .build()
        val bitmap: Bitmap = loader.execute(request).image?.toBitmap() ?: return LogoTone.Unknown

        val scale = min(1f, 96f / max(bitmap.width, bitmap.height).toFloat())
        val width = max(1, (bitmap.width * scale).toInt())
        val height = max(1, (bitmap.height * scale).toInt())
        val small = if (scale < 1f) bitmap.scale(width, height) else bitmap

        val pixels = IntArray(small.width * small.height)
        small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
        toneOfPixels(pixels)
    }.getOrDefault(LogoTone.Unknown)

private fun Bitmap.scale(width: Int, height: Int): Bitmap =
    Bitmap.createScaledBitmap(this, width, height, true)
