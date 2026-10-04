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
class AppContainer(val context: Context) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val crashes by lazy { com.cineverse.app.core.crash.CrashReporter(context) }

    val awards by lazy { com.cineverse.app.data.awards.AwardsRepository(http) }

    val statsSections by lazy {
        com.cineverse.app.data.prefs.StatsSectionsRepository(Firebase.firestore(context), auth, scope)
    }

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

    val tmdb: TmdbRepository by lazy {
        TmdbRepository(retrofit.create(TmdbApi::class.java)).also { repo ->
            // Episode lengths TMDB leaves blank, from TVmaze.
            repo.runtimeFallback = { show -> airing.times.runtime(show.id, show.imdbId, show.title, show.releaseDate) }
            // Adult titles are remembered as they are seen, so Gemini is never given them.
            repo.onDetail = { detail -> privacy.saw(detail.key, detail.adult) }
            // The Top 10 as it is each week, the same ten Home and the chart show.
            repo.onWeeklyChart = { type, items ->
                topTenHistory.record(type, items.filterNot { it.type == com.cineverse.app.data.model.MediaType.Tv && it.genreIds.any { g -> g == 10767 || g == 10763 } })
            }
        }
    }

    val recommender: Recommender by lazy { Recommender(tmdb) }

    val boxOffice by lazy { com.cineverse.app.data.boxoffice.BoxOfficeRepository(tmdb, http) }

    val unlockedLists by lazy { com.cineverse.app.data.lock.UnlockedLists() }

    val continueOrder by lazy {
        com.cineverse.app.data.firebase.ContinuePrefsRepository(Firebase.firestore(context), auth, scope)
    }

    val gemini by lazy { com.cineverse.app.data.ai.Gemini(context) { settings.settings.value.geminiOn } }

    val assistant by lazy { com.cineverse.app.data.ai.Assistant(this) }

    val persona by lazy { com.cineverse.app.data.ai.Persona(this) }

    val forYou by lazy { com.cineverse.app.data.ai.ForYou(gemini, persona) }

    val titleChat by lazy { com.cineverse.app.data.ai.TitleChat(gemini) }

    /** Home's row for this time of day, by Gemini. */
    val momentRail by lazy { com.cineverse.app.data.ai.MomentRail(this) }

    /** A lighter suggestion after a run of heavy viewing. */
    val palate by lazy { com.cineverse.app.data.ai.PalateCleanser(this) }

    /** "Why did I like it?" across your top scores. */
    val tenPattern by lazy { com.cineverse.app.data.ai.TenPattern(this) }

    /** Gemini's spotlight on people and studios. */
    val spotlight by lazy { com.cineverse.app.data.ai.Spotlight(this) }

    /** Hooks for synopses, and critics in a line. */
    val lines by lazy { com.cineverse.app.data.ai.Lines(this) }

    /** "Finish or drop?" for titles left half-watched. */
    val halfWatched by lazy { com.cineverse.app.data.ai.HalfWatched(this) }

    /** TMDB's Top 250 ranks. */
    val topRated by lazy { com.cineverse.app.data.charts.TopRated(context, tmdb) }

    /** The weekly Top 10, week by week. */
    val topTenHistory by lazy { com.cineverse.app.data.charts.TopTenHistory(context) }

    /** Gemini's moods for the watchlist. */
    val moodTags by lazy { com.cineverse.app.data.ai.MoodTags(this) }

    /** What Gemini is never told: adult titles and titles in locked lists. */
    val privacy by lazy { com.cineverse.app.data.ai.GeminiPrivacy(this) }

    /** "Should I skip this?" per episode, by Gemini. */
    val skipAdvice by lazy { com.cineverse.app.data.ai.SkipAdvice(this) }

    /** Two titles side by side, by Gemini. */
    val compare by lazy { com.cineverse.app.data.ai.Compare(this) }

    /** "Your taste in one paragraph", monthly. */
    val tasteParagraph by lazy { com.cineverse.app.data.ai.TasteParagraph(this) }

    /** "Based on" and box office, from Wikidata. */
    val wikiFacts by lazy { com.cineverse.app.data.wiki.WikiFactsRepository(context, http) }

    /** Where titles were filmed, from Wikidata. */
    val filmingLocations by lazy { com.cineverse.app.data.places.FilmingLocations(context, http) }

    /** Each episode's IMDb score (with an OMDb key) or TVmaze's. */
    val episodeScores by lazy { com.cineverse.app.data.scores.EpisodeScores(context, http) { settings.settings.value.omdbKey } }

    /** Older watched titles' missing details, filled in quietly. */
    val backfill by lazy { com.cineverse.app.data.firebase.WatchedBackfill(this) }

    /** The Diary's line for each month. */
    val monthBlurb by lazy { com.cineverse.app.data.ai.MonthBlurb(context, gemini) }

    /** The one-line hook on a new-episode alert. */
    val episodeHook by lazy { com.cineverse.app.data.ai.EpisodeHook(this) }

    /** Three facts per title, once it has been watched. */
    val trivia by lazy { com.cineverse.app.data.ai.Trivia(context, gemini) }

    /** Each title's "Ask about it" conversation, kept across visits. */
    val chatStore by lazy { com.cineverse.app.data.ai.ChatStore(context) }

    val tonight by lazy { com.cineverse.app.data.ai.Tonight(this) }

    val speaker by lazy { com.cineverse.app.data.ai.Speaker(context) }

    val celebrations by lazy {
        com.cineverse.app.data.badges.Celebrations(context, auth, library.library, episodes.progress, scope)
    }

    val watchingNow by lazy { com.cineverse.app.notify.WatchingNowRepository(context) }

    val previouslyOn by lazy { com.cineverse.app.data.recap.PreviouslyOn(context, gemini) }

    val inbox by lazy {
        com.cineverse.app.data.inbox.InboxRepository(context, library.library, episodes.progress, airing, scope)
    }

    val castHours by lazy { com.cineverse.app.data.cast.CastHoursRepository(context, tmdb) }

    val airing by lazy {
        com.cineverse.app.data.airing.AiringRepository(
            tmdb,
            com.cineverse.app.data.airing.ExactTimes(context, http),
        )
    }

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
