package com.cineverse.app.core.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A title page's own colour, taken from its poster - the website's
 * js/ambient.js. The glow behind the header and the primary button wear it, so
 * Dune is sand and The Matrix is green rather than every page being the same
 * red.
 *
 * The colour is the poster's dominant VIVID hue: greys, near-blacks and blown
 * whites are ignored, because a poster that is mostly a black background is not
 * a black film. Too little real colour and the page keeps CineVerse red - a
 * muddy accent is worse than the brand one. The result is lifted to a
 * brightness that reads on the dark page.
 */
val LocalTitleAccent = staticCompositionLocalOf<Color?> { null }

private val accentCache = LruCacheLite<String, Int>(64)

@Composable
fun rememberPosterAccent(posterPath: String?, enabled: Boolean): State<Color?> {
    val context = LocalContext.current
    return produceState<Color?>(initialValue = posterPath?.let { accentCache[it]?.toAccent() }, posterPath, enabled) {
        if (!enabled || posterPath == null) { value = null; return@produceState }
        accentCache[posterPath]?.let { value = it.toAccent(); return@produceState }
        val found = withContext(Dispatchers.Default) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(Img.tiny(posterPath))
                    .allowHardware(false)
                    .build()
                val bitmap = SingletonImageLoader.get(context).execute(request).image?.toBitmap()
                bitmap?.let(::dominantVividColor)
            }.getOrNull()
        }
        accentCache.put(posterPath, found ?: NONE)
        value = found?.toAccent()
    }
}

private const val NONE = 0

private fun Int.toAccent(): Color? = if (this == NONE) null else Color(this)

/** The dominant vivid hue of a small bitmap, as an ARGB int lifted for a dark page, or null. */
internal fun dominantVividColor(bitmap: Bitmap): Int? {
    val scaled = if (bitmap.width > 64) Bitmap.createScaledBitmap(bitmap, 64, (64f * bitmap.height / bitmap.width).toInt().coerceAtLeast(1), true) else bitmap
    val pixels = IntArray(scaled.width * scaled.height)
    scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
    return dominantVividColor(pixels)
}

/** Pure, for tests: the same over raw ARGB pixels. */
internal fun dominantVividColor(pixels: IntArray): Int? {
    val buckets = 24
    val weight = DoubleArray(buckets)
    val sumH = DoubleArray(buckets)
    val sumS = DoubleArray(buckets)
    val hsv = FloatArray(3)
    for (pixel in pixels) {
        if ((pixel ushr 24) < 200) continue
        android.graphics.Color.colorToHSV(pixel, hsv)
        val s = hsv[1]
        val v = hsv[2]
        if (s < 0.35f || v < 0.25f || v > 0.97f) continue
        val w = (s * v).toDouble()
        val bucket = ((hsv[0] / 360f) * buckets).toInt().coerceIn(0, buckets - 1)
        weight[bucket] += w
        sumH[bucket] += hsv[0] * w
        sumS[bucket] += s * w
    }
    val best = weight.indices.maxByOrNull { weight[it] } ?: return null
    // At least a few percent of the poster has to be this colour.
    if (weight[best] < pixels.size * 0.035) return null
    val hue = (sumH[best] / weight[best]).toFloat()
    val saturation = (sumS[best] / weight[best]).toFloat().coerceIn(0.5f, 0.85f)
    return android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 0.92f))
}

/** A tiny synchronized LRU, local so this file stands on its own. */
private class LruCacheLite<K : Any, V : Any>(private val max: Int) {
    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > max
    }
    @Synchronized operator fun get(key: K): V? = map[key]
    @Synchronized fun put(key: K, value: V) { map[key] = value }
}
