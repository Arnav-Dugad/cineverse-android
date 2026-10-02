package com.cineverse.app.core.net

import android.content.Context
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * One HTTP client for the whole app.
 *
 * The important part is the CACHE, and it is doing the job the website needed a
 * hand-written store for. TMDB sends no useful `Cache-Control`, so [CacheRules]
 * rewrites the response's headers on the way in — a catalogue page is good for
 * ten minutes, a title's own details for a day, a person for a week. OkHttp then
 * serves repeats from disk without a request, which is why opening a title you
 * looked at this morning paints instantly and works on a plane.
 *
 * [OfflineFirst] is the other half: with no network, the client is allowed to
 * serve anything up to a week stale rather than failing. The app would rather
 * show you yesterday's trending row than an error page.
 */
object Http {

    /** Lenient on purpose: TMDB adds fields, and a new one must never be fatal. */
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    private const val CACHE_BYTES = 192L * 1024 * 1024

    fun client(context: Context, isOnline: () -> Boolean): OkHttpClient {
        val cache = Cache(File(context.cacheDir, "http"), CACHE_BYTES)
        return OkHttpClient.Builder()
            .cache(cache)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(OfflineFirst(isOnline))
            .addNetworkInterceptor(CacheRules)
            .build()
    }

    /**
     * How long each kind of answer stays fresh. Matched to how fast the thing
     * behind it actually changes, not to one number for everything.
     */
    private object CacheRules : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            val path = chain.request().url.encodedPath
            val seconds = when {
                // Configuration and genre lists change a few times a decade.
                path.contains("/configuration") || path.contains("/genre/") -> 30 * 24 * 3600
                // A person's filmography moves slowly.
                path.startsWith("/3/person/") -> 7 * 24 * 3600
                // A title's own facts: cast, runtime, where to watch.
                path.startsWith("/3/movie/") || path.startsWith("/3/tv/") -> 24 * 3600
                // Lists that reorder constantly.
                path.contains("/trending/") || path.contains("/popular") -> 10 * 60
                path.startsWith("/3/search/") || path.startsWith("/3/discover/") -> 10 * 60
                else -> 60 * 60
            }
            return response.newBuilder()
                .removeHeader("Pragma")
                .removeHeader("Expires")
                .header("Cache-Control", "public, max-age=$seconds")
                .build()
        }
    }

    /**
     * Offline, the cache stops being an optimisation and becomes the product.
     * A week is long enough to cover a flight and short enough that nobody is
     * shown a "now playing" row from last month.
     */
    private class OfflineFirst(private val isOnline: () -> Boolean) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            if (isOnline()) return chain.proceed(chain.request())
            val offline = chain.request().newBuilder()
                .cacheControl(
                    CacheControl.Builder()
                        .onlyIfCached()
                        .maxStale(7, TimeUnit.DAYS)
                        .build()
                )
                .build()
            return chain.proceed(offline)
        }
    }
}
