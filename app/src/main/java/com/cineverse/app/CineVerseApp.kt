package com.cineverse.app

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath
import com.cineverse.app.core.net.Http
import com.cineverse.app.data.firebase.AuthRepository
import com.cineverse.app.data.firebase.EpisodeRepository
import com.cineverse.app.data.firebase.Firebase
import com.cineverse.app.data.firebase.LibraryRepository
import com.cineverse.app.data.prefs.SettingsRepository
import com.cineverse.app.data.recommend.Recommender
import com.cineverse.app.data.scores.OmdbVerdict
import com.cineverse.app.data.scores.ScoresRepository
import com.cineverse.app.data.tmdb.TmdbApi
import com.cineverse.app.data.tmdb.TmdbRepository
import com.cineverse.app.update.UpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType

private val JsonMedia = "application/json".toMediaType()

class CineVerseApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Installed FIRST, before anything else can throw, so the handler is in
        // place for the rest of start-up and not only for the part after it.
        container.crashes.install()
        // The channels must exist before anything can post to them, and they are
        // cheap to recreate: the system keeps whatever the user changed.
        //
        // The daily sweep is NOT scheduled here. WorkManager initialises itself
        // through an app-startup provider, and touching it from
        // Application.onCreate reaches it before that provider has run -- which
        // throws, taking the whole process with it. It is scheduled from the
        // activity instead, by which time everything is up.
        com.cineverse.app.notify.Notifications.ensureChannels(this)
    }

    /**
     * Coil, sharing the app's OkHttp client so artwork lands in the same disk
     * cache as everything else and obeys the same offline rules. 320 MB is
     * generous on purpose: posters are the app, and a cache miss on a rail you
     * scroll past every day is the most visible kind of slowness there is.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { container.http }))
            }
            .memoryCache {
                MemoryCache.Builder().maxSizePercent(context, 0.25).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("artwork").toOkioPath())
                    .maxSizeBytes(320L * 1024 * 1024)
                    .build()
            }
            .crossfade(220)
            .build()
}

/**
 * The object graph, by hand.
 *
 * There is no dependency-injection framework here, and that is a decision rather
 * than an omission: the graph is a dozen singletons with no cycles and no
 * scoping beyond "one per process". A framework would add an annotation
 * processor, a build-time code generation step and a class of error that only
 * appears at runtime, to replace twenty lines of `by lazy`.
 */
class AppContainer(private val context: Context) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val crashes by lazy { com.cineverse.app.core.crash.CrashReporter(context) }

    // ---------- is there a network ----------

    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    init {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        fun reread() {
            // Ask the system what the ACTIVE network is rather than counting
            // callbacks: a device with Wi-Fi and mobile data up raises and loses
            // several, and a flag toggled by them drifts out of step with
            // reality within minutes.
            _online.value = manager?.activeNetwork
                ?.let { manager.getNetworkCapabilities(it) }
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
        }
        reread()
        runCatching {
            manager?.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) = reread()
                    override fun onLost(network: Network) = reread()
                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities,
                    ) = reread()
                },
            )
        }
    }

    /** True on Wi-Fi or ethernet — what "autoplay on Wi-Fi only" actually asks. */
    val unmetered: Boolean
        get() = runCatching {
            val manager = context.getSystemService(ConnectivityManager::class.java)
            val capabilities = manager?.activeNetwork?.let { manager.getNetworkCapabilities(it) }
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
        }.getOrDefault(false)

    // ---------- http ----------

    val http: OkHttpClient by lazy {
        Http.client(context).newBuilder()
            .addInterceptor(TmdbKeyInterceptor)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        @Suppress("OPT_IN_USAGE")
        Retrofit.Builder()
            .baseUrl(TmdbApi.BASE_URL)
            .client(http)
            .addConverterFactory(Http.json.asConverterFactory(JsonMedia))
            .build()
    }

    val tmdb: TmdbRepository by lazy { TmdbRepository(retrofit.create(TmdbApi::class.java)) }

    val recommender: Recommender by lazy { Recommender(tmdb) }

    /**
     * Bumped once per launch, never on navigation.
     *
     * It is what makes the rails a different slice of the ranked pool each time
     * the app is opened. Bumping it while someone is browsing would reshuffle a
     * row under their thumb, which reads as a bug rather than as freshness.
     */
    val rotation: Int = (System.currentTimeMillis() / 1000L % 100_000L).toInt()

    // ---------- firebase ----------

    val auth: AuthRepository by lazy {
        AuthRepository(Firebase.auth(context), Firebase.firestore(context), scope)
    }

    val library: LibraryRepository by lazy {
        LibraryRepository(Firebase.firestore(context), auth, scope)
    }

    val episodes: EpisodeRepository by lazy {
        EpisodeRepository(Firebase.firestore(context), auth, scope)
    }

    val settings: SettingsRepository by lazy {
        SettingsRepository(context, Firebase.firestore(context), auth, scope)
    }

    // ---------- outside scores ----------

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** One-off things the app needs to say: a toast, a snackbar. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun say(message: String) { _messages.tryEmit(message) }

    val scores: ScoresRepository by lazy {
        ScoresRepository(
            client = http,
            omdbKey = { settings.settings.value.omdbKey },
            onOmdbTrouble = { verdict -> say(ScoresRepository.troubleMessage(verdict)) },
        )
    }

    val updates: UpdateRepository by lazy { UpdateRepository(context, http, settings) }

    private object TmdbKeyInterceptor : Interceptor {
        /**
         * The same key the website uses. A TMDB v3 key is a read-only
         * identifier for an application, not a credential for a user — it is in
         * the website's own shipped JavaScript for exactly that reason.
         */
        private const val KEY = "2b834d5234781ad70bd646922e9ddd18"

        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val request = chain.request()
            if (!request.url.host.contains("themoviedb.org")) return chain.proceed(request)
            val url = request.url.newBuilder().addQueryParameter("api_key", KEY).build()
            return chain.proceed(request.newBuilder().url(url).build())
        }
    }
}
