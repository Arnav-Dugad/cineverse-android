package com.cineverse.app.core.net

import android.content.Context
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File
import java.io.IOException
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
 * [OfflineFallback] is the other half, and the shape of it matters. The obvious
 * design — ask "are we online?" and force cache-only when the answer is no — was
 * wrong, and running the app proved it: one wrong reading of connectivity and
 * every request in the app returns `504 Unsatisfiable Request (only-if-cached)`,
 * including requests that would have worked perfectly. The network is the source
 * of truth about whether the network works, so the client ALWAYS tries it and
 * reaches for the cache only once a request has actually failed.
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

    /** The only hosts whose cache headers this app is entitled to rewrite. */
    private val TMDB_HOSTS = setOf("api.themoviedb.org", "image.tmdb.org")

    fun client(context: Context): OkHttpClient {
        val cache = Cache(File(context.cacheDir, "http"), CACHE_BYTES)
        return OkHttpClient.Builder()
            .cache(cache)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(OfflineFallback)
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
            // ONLY TMDB.
            //
            // These rules exist because TMDB sends no usable Cache-Control, and
            // for a long time they were applied to every request the app made,
            // including GitHub's. The fall-through of an hour then meant that
            // checking for updates five minutes after a release went out was
            // answered from disk with the previous release - the app saying it
            // was up to date while a new version sat on the other end of a
            // request it never made. A server that does send cache headers is
            // telling you something, and overwriting that is not caching, it is
            // guessing.
            if (chain.request().url.host !in TMDB_HOSTS) return response
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
     * When a request genuinely fails, serve what we already have.
     *
     * A week is long enough to cover a flight and short enough that nobody is
     * shown a "now playing" row from last month. If the cache misses too, the
     * 504 propagates and the screen above decides what to say — which for a rail
     * is "nothing", and for a title page is a retry button.
     */
    private object OfflineFallback : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            return try {
                chain.proceed(request)
            } catch (error: IOException) {
                chain.proceed(
                    request.newBuilder()
                        .cacheControl(
                            CacheControl.Builder()
                                .onlyIfCached()
                                .maxStale(7, TimeUnit.DAYS)
                                .build()
                        )
                        .build()
                )
            }
        }
    }
}
