package com.cineverse.app.data.prefs

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cineverse.app.core.design.MotionChoice
import com.cineverse.app.core.design.ThemeChoice
import com.cineverse.app.data.firebase.AuthRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("cineverse")

/**
 * The app's settings.
 *
 * Stored on the device first, so they apply before anything has loaded, and
 * mirrored to `users/{uid}.experiencePrefs` — the SAME document the website
 * reads and writes. The newest `_updatedAt` wins, which is the rule the website
 * already uses, so the two can be changed in either order on either device and
 * converge.
 *
 * [detailOrder] is the clearest example of why that matters: arrange a title
 * page on the laptop and the phone's About section is in your order the next
 * time it opens.
 */
@Immutable
data class Settings(
    val theme: ThemeChoice = ThemeChoice.System,
    val dynamicColor: Boolean = false,
    val motion: MotionChoice = MotionChoice.System,
    val haptics: Boolean = true,
    val region: String = "IN",
    val showRatings: Boolean = true,
    val showWatched: Boolean = true,
    val spoilerShield: Boolean = false,
    /** The hero's trailer plays by itself. Off by default on mobile data. */
    val autoplay: Boolean = true,
    val autoplayOnWifiOnly: Boolean = true,
    val posterCaptions: Boolean = false,
    val mature: Boolean = false,
    val matureBlur: Boolean = true,
    val omdbKey: String = "",
    /** The order a title page's About rows read in, shared with the website. */
    val detailOrder: List<String> = emptyList(),
    val notifyNewEpisodes: Boolean = true,
    val notifyReleases: Boolean = true,
    /** The minute an episode airs, where the broadcaster's time is known. */
    val notifyAiring: Boolean = true,
    /** A new season of a show you have watched. */
    val notifySeasons: Boolean = true,
    /** Your month in films and series, on the first. */
    val notifyRecap: Boolean = true,
    /** A new version of the app. */
    val notifyUpdates: Boolean = true,
    /** The hero advances by itself. Off leaves it on one title until swiped. */
    val heroAutoAdvance: Boolean = true,
    /** How long each hero slide is held, in seconds, when nothing is playing. */
    val heroSeconds: Int = 7,
    /** How wide a poster is drawn: more per row, or fewer and larger. */
    val gridDensity: GridDensity = GridDensity.Comfortable,
    /** Which tab the app opens on. */
    val startTab: String = "home",
    /** The burst on a perfect ten. */
    val confetti: Boolean = true,
    /** Shake the phone on Discover for Surprise me, or on your list to pick from it. */
    val shakeToPick: Boolean = true,
    /** Every Gemini feature, and every trace of it in the UI. On by default. */
    val geminiOn: Boolean = true,
    /** The navigation bar never tucks away, and shows on every page. */
    val pinNavBar: Boolean = false,
    /** The streaming services you pay for, as TMDB provider ids. */
    val myServices: Set<Int> = emptySet(),
    /** Voice search reads its answer aloud. */
    val spokenAnswers: Boolean = true,
    /** Swipe an episode row to mark everything up to it. */
    val episodeSwipe: Boolean = true,
    /** The live countdown to the next episode on a title page. */
    val countdowns: Boolean = true,
    /** Names under posters. Off by default: a poster already says what it is. */
    val posterMeta: Boolean = false,
    val posterCorner: String = "Rounded",
    val posterMatch: Boolean = true,
    /** The title's own logo on its page, rather than the name set in our face. */
    val titleLogos: Boolean = true,
    /** No title over a title page at all - neither its logo nor its name. On by default. */
    val hideTitle: Boolean = true,
    /** A title page takes its accent from its own poster. */
    val titleColour: Boolean = true,
    val updatedAt: Long = 0L,
) {
    val adult: Boolean get() = mature
}

/**
 * How tightly posters are packed.
 *
 * A column count for a phone - four, three or two - which wider windows add
 * to (see posterColumns), and a matching card width for rails. It was a
 * minimum cell width once, and two of its three choices gave the same two
 * columns on a phone, so the setting appeared to do nothing.
 */
enum class GridDensity(val label: String, val columns: Int, val railDp: Int) {
    Dense("More per row", 4, 112),
    Comfortable("Comfortable", 3, 132),
    Large("Larger posters", 2, 156),
}

class SettingsRepository(
    private val context: Context,
    private val store: FirebaseFirestore,
    private val auth: AuthRepository,
    private val scope: CoroutineScope,
) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamicColor")
        val motion = stringPreferencesKey("motion")
        val haptics = booleanPreferencesKey("haptics")
        val region = stringPreferencesKey("region")
        val showRatings = booleanPreferencesKey("showRatings")
        val showWatched = booleanPreferencesKey("showWatched")
        val spoilerShield = booleanPreferencesKey("spoilerShield")
        val autoplay = booleanPreferencesKey("autoplay")
        val autoplayWifi = booleanPreferencesKey("autoplayWifi")
        val posterCaptions = booleanPreferencesKey("posterCaptions")
        val mature = booleanPreferencesKey("mature")
        val matureBlur = booleanPreferencesKey("matureBlur")
        val omdbKey = stringPreferencesKey("omdbKey")
        val detailOrder = stringPreferencesKey("detailOrder")
        val notifyEpisodes = booleanPreferencesKey("notifyEpisodes")
        val heroAutoAdvance = booleanPreferencesKey("heroAutoAdvance")
        val heroSeconds = intPreferencesKey("heroSeconds")
        val gridDensity = stringPreferencesKey("gridDensity")
        val startTab = stringPreferencesKey("startTab")
        val confetti = booleanPreferencesKey("confetti")
        val shakeToPick = booleanPreferencesKey("shakeToPick")
        val spokenAnswers = booleanPreferencesKey("spokenAnswers")
        val geminiOn = booleanPreferencesKey("geminiOn")
        val pinNavBar = booleanPreferencesKey("pinNavBar")
        val myServices = stringPreferencesKey("myServices")
        val episodeSwipe = booleanPreferencesKey("episodeSwipe")
        val countdowns = booleanPreferencesKey("countdowns")
        val posterMeta = booleanPreferencesKey("posterMeta")
        val posterCorner = stringPreferencesKey("posterCorner")
        val posterMatch = booleanPreferencesKey("posterMatch")
        val titleLogos = booleanPreferencesKey("titleLogos")
        val titleColour = booleanPreferencesKey("titleColour")
        val hideTitle = booleanPreferencesKey("hideTitle")
        val notifyReleases = booleanPreferencesKey("notifyReleases")
        val notifyAiring = booleanPreferencesKey("notifyAiring")
        val notifySeasons = booleanPreferencesKey("notifySeasons")
        val notifyRecap = booleanPreferencesKey("notifyRecap")
        val notifyUpdates = booleanPreferencesKey("notifyUpdates")
        val updatedAt = longPreferencesKey("updatedAt")
        val lastUpdateCheck = longPreferencesKey("lastUpdateCheck")
        val skippedVersion = intPreferencesKey("skippedVersion")
        val seenVersion = intPreferencesKey("seenVersion")
        val notified = stringSetPreferencesKey("notified")
    }

    val settings: StateFlow<Settings> = context.dataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, Settings())

    private fun Preferences.toSettings() = Settings(
        theme = enumOf(this[Keys.theme], ThemeChoice.System),
        dynamicColor = this[Keys.dynamicColor] ?: false,
        motion = enumOf(this[Keys.motion], MotionChoice.System),
        haptics = this[Keys.haptics] ?: true,
        region = this[Keys.region] ?: "IN",
        showRatings = this[Keys.showRatings] ?: true,
        showWatched = this[Keys.showWatched] ?: true,
        spoilerShield = this[Keys.spoilerShield] ?: false,
        autoplay = this[Keys.autoplay] ?: true,
        autoplayOnWifiOnly = this[Keys.autoplayWifi] ?: true,
        posterCaptions = this[Keys.posterCaptions] ?: false,
        mature = this[Keys.mature] ?: false,
        matureBlur = this[Keys.matureBlur] ?: true,
        omdbKey = this[Keys.omdbKey] ?: "",
        detailOrder = this[Keys.detailOrder]?.split(',')?.filter { it.isNotBlank() }.orEmpty(),
        notifyNewEpisodes = this[Keys.notifyEpisodes] ?: true,
        heroAutoAdvance = this[Keys.heroAutoAdvance] ?: true,
        heroSeconds = (this[Keys.heroSeconds] ?: 7).coerceIn(4, 30),
        gridDensity = runCatching {
            GridDensity.valueOf(this[Keys.gridDensity] ?: GridDensity.Comfortable.name)
        }.getOrDefault(GridDensity.Comfortable),
        startTab = this[Keys.startTab] ?: "home",
        confetti = this[Keys.confetti] ?: true,
        shakeToPick = this[Keys.shakeToPick] ?: true,
        spokenAnswers = this[Keys.spokenAnswers] ?: true,
        geminiOn = this[Keys.geminiOn] ?: true,
        pinNavBar = this[Keys.pinNavBar] ?: false,
        myServices = this[Keys.myServices].orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet(),
        episodeSwipe = this[Keys.episodeSwipe] ?: true,
        countdowns = this[Keys.countdowns] ?: true,
        posterMeta = this[Keys.posterMeta] ?: false,
        posterCorner = this[Keys.posterCorner] ?: "Rounded",
        posterMatch = this[Keys.posterMatch] ?: true,
        titleLogos = this[Keys.titleLogos] ?: true,
        titleColour = this[Keys.titleColour] ?: true,
        hideTitle = this[Keys.hideTitle] ?: true,
        notifyReleases = this[Keys.notifyReleases] ?: true,
        notifyAiring = this[Keys.notifyAiring] ?: true,
        notifySeasons = this[Keys.notifySeasons] ?: true,
        notifyRecap = this[Keys.notifyRecap] ?: true,
        notifyUpdates = this[Keys.notifyUpdates] ?: true,
        updatedAt = this[Keys.updatedAt] ?: 0L,
    )

    private inline fun <reified T : Enum<T>> enumOf(name: String?, fallback: T): T =
        runCatching { enumValueOf<T>(name ?: "") }.getOrDefault(fallback)

    /** Every change stamps `updatedAt` and pushes the snapshot to the cloud. */
    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit {
            block(it)
            it[Keys.updatedAt] = System.currentTimeMillis()
        }
        push()
    }

    suspend fun setTheme(value: ThemeChoice) = edit { it[Keys.theme] = value.name }
    suspend fun setDynamicColor(value: Boolean) = edit { it[Keys.dynamicColor] = value }
    suspend fun setMotion(value: MotionChoice) = edit { it[Keys.motion] = value.name }
    suspend fun setHaptics(value: Boolean) = edit { it[Keys.haptics] = value }
    suspend fun setRegion(value: String) = edit { it[Keys.region] = value.uppercase().take(2) }
    suspend fun setShowRatings(value: Boolean) = edit { it[Keys.showRatings] = value }
    suspend fun setShowWatched(value: Boolean) = edit { it[Keys.showWatched] = value }
    suspend fun setSpoilerShield(value: Boolean) = edit { it[Keys.spoilerShield] = value }
    suspend fun setAutoplay(value: Boolean) = edit { it[Keys.autoplay] = value }
    suspend fun setAutoplayWifiOnly(value: Boolean) = edit { it[Keys.autoplayWifi] = value }
    suspend fun setPosterCaptions(value: Boolean) = edit { it[Keys.posterCaptions] = value }
    suspend fun setMature(value: Boolean) = edit { it[Keys.mature] = value }
    suspend fun setMatureBlur(value: Boolean) = edit { it[Keys.matureBlur] = value }
    suspend fun setOmdbKey(value: String) = edit { it[Keys.omdbKey] = value.trim().take(32) }
    suspend fun setHeroAutoAdvance(value: Boolean) = edit { it[Keys.heroAutoAdvance] = value }
    suspend fun setHeroSeconds(value: Int) = edit { it[Keys.heroSeconds] = value.coerceIn(4, 30) }
    suspend fun setGridDensity(value: GridDensity) = edit { it[Keys.gridDensity] = value.name }
    suspend fun setStartTab(value: String) = edit { it[Keys.startTab] = value }
    suspend fun setConfetti(value: Boolean) = edit { it[Keys.confetti] = value }
    suspend fun setShakeToPick(value: Boolean) = edit { it[Keys.shakeToPick] = value }
    suspend fun setSpokenAnswers(value: Boolean) = edit { it[Keys.spokenAnswers] = value }
    suspend fun setGeminiOn(value: Boolean) = edit { it[Keys.geminiOn] = value }
    suspend fun setPinNavBar(value: Boolean) = edit { it[Keys.pinNavBar] = value }
    suspend fun setMyServices(value: Set<Int>) = edit { it[Keys.myServices] = value.sorted().joinToString(",") }
    suspend fun setEpisodeSwipe(value: Boolean) = edit { it[Keys.episodeSwipe] = value }
    suspend fun setCountdowns(value: Boolean) = edit { it[Keys.countdowns] = value }
    suspend fun setPosterMeta(value: Boolean) = edit { it[Keys.posterMeta] = value }
    suspend fun setPosterCorner(value: String) = edit { it[Keys.posterCorner] = value }
    suspend fun setPosterMatch(value: Boolean) = edit { it[Keys.posterMatch] = value }
    suspend fun setTitleLogos(value: Boolean) = edit { it[Keys.titleLogos] = value }
    suspend fun setTitleColour(value: Boolean) = edit { it[Keys.titleColour] = value }
    suspend fun setHideTitle(value: Boolean) = edit { it[Keys.hideTitle] = value }

    suspend fun setNotifyEpisodes(value: Boolean) = edit { it[Keys.notifyEpisodes] = value }
    suspend fun setNotifyReleases(value: Boolean) = edit { it[Keys.notifyReleases] = value }
    suspend fun setNotifyAiring(value: Boolean) = edit { it[Keys.notifyAiring] = value }
    suspend fun setNotifySeasons(value: Boolean) = edit { it[Keys.notifySeasons] = value }
    suspend fun setNotifyRecap(value: Boolean) = edit { it[Keys.notifyRecap] = value }
    suspend fun setNotifyUpdates(value: Boolean) = edit { it[Keys.notifyUpdates] = value }
    suspend fun setDetailOrder(value: List<String>) = edit { it[Keys.detailOrder] = value.joinToString(",") }

    /** Not synced: when this device last asked GitHub about an update. */
    suspend fun markUpdateChecked() = context.dataStore.edit {
        it[Keys.lastUpdateCheck] = System.currentTimeMillis()
    }

    val lastUpdateCheck: StateFlow<Long> = context.dataStore.data
        .map { it[Keys.lastUpdateCheck] ?: 0L }
        .stateIn(scope, SharingStarted.Eagerly, 0L)

    suspend fun skipVersion(code: Int) = context.dataStore.edit { it[Keys.skippedVersion] = code }

    /**
     * The newest version whose notes the user has been shown.
     *
     * Zero means "has never been recorded", which is a FIRST install rather than
     * an update: showing someone a changelog for software they have never run is
     * the kind of thing that reads as a bug.
     */
    suspend fun markVersionSeen(code: Int) = context.dataStore.edit { it[Keys.seenVersion] = code }

    val seenVersion: StateFlow<Int> = context.dataStore.data
        .map { it[Keys.seenVersion] ?: 0 }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    /**
     * Everything the daily sweep has already said out loud.
     *
     * Capped at 400 keys, newest kept. An uncapped set grows by a few entries a
     * day for ever and is read on every sweep; 400 is comfortably more than a
     * year of episodes for a library this size, and the ones that fall off the
     * end are months old and could not fire again anyway.
     */
    val notifiedKeys: StateFlow<Set<String>> = context.dataStore.data
        .map { it[Keys.notified] ?: emptySet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    suspend fun rememberNotified(keys: Set<String>) = context.dataStore.edit {
        it[Keys.notified] = keys.toList().takeLast(400).toSet()
    }

    val skippedVersion: StateFlow<Int> = context.dataStore.data
        .map { it[Keys.skippedVersion] ?: 0 }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    // ---------- the cloud mirror ----------

    /** Pull the website's snapshot if it is newer than this device's. */
    fun syncFromCloud() = scope.launch {
        val uid = auth.uid.value ?: return@launch
        runCatching {
            val doc = store.collection("users").document(uid).get().await()
            @Suppress("UNCHECKED_CAST")
            val cloud = doc.get("experiencePrefs") as? Map<String, Any?> ?: return@launch
            val cloudAt = (cloud["_updatedAt"] as? Number)?.toLong() ?: 0L
            if (cloudAt <= settings.value.updatedAt) return@launch
            context.dataStore.edit { prefs ->
                (cloud["theme"] as? String)?.let {
                    prefs[Keys.theme] = when (it) {
                        "dark" -> ThemeChoice.Dark.name
                        "light" -> ThemeChoice.Light.name
                        else -> ThemeChoice.System.name
                    }
                }
                (cloud["motion"] as? String)?.let {
                    prefs[Keys.motion] = when (it) {
                        "full" -> MotionChoice.Full.name
                        "reduced" -> MotionChoice.Reduced.name
                        else -> MotionChoice.System.name
                    }
                }
                (cloud["haptics"] as? Boolean)?.let { prefs[Keys.haptics] = it }
                (cloud["region"] as? String)?.let { prefs[Keys.region] = it }
                (cloud["showRatings"] as? Boolean)?.let { prefs[Keys.showRatings] = it }
                (cloud["showWatched"] as? Boolean)?.let { prefs[Keys.showWatched] = it }
                (cloud["spoilerShield"] as? Boolean)?.let { prefs[Keys.spoilerShield] = it }
                (cloud["autoplay"] as? Boolean)?.let { prefs[Keys.autoplay] = it }
                (cloud["mature"] as? Boolean)?.let { prefs[Keys.mature] = it }
                (cloud["matureBlur"] as? Boolean)?.let { prefs[Keys.matureBlur] = it }
                (cloud["omdbKey"] as? String)?.let { prefs[Keys.omdbKey] = it }
                (cloud["hidePosterCaptions"] as? Boolean)?.let { prefs[Keys.posterCaptions] = !it }
                (cloud["detailOrder"] as? List<*>)?.let { order ->
                    prefs[Keys.detailOrder] = order.filterIsInstance<String>().joinToString(",")
                }
                prefs[Keys.updatedAt] = cloudAt
            }
        }
    }

    /** Push this device's snapshot, in the shape the website expects to read. */
    private suspend fun push() {
        val uid = auth.uid.value ?: return
        val current = settings.value
        runCatching {
            store.collection("users").document(uid).set(
                mapOf(
                    "experiencePrefs" to mapOf(
                        "theme" to when (current.theme) {
                            ThemeChoice.Dark -> "dark"
                            ThemeChoice.Light -> "light"
                            ThemeChoice.System -> "system"
                        },
                        "motion" to when (current.motion) {
                            MotionChoice.Full -> "full"
                            MotionChoice.Reduced -> "reduced"
                            MotionChoice.System -> "system"
                        },
                        "haptics" to current.haptics,
                        "region" to current.region,
                        "showRatings" to current.showRatings,
                        "showWatched" to current.showWatched,
                        "spoilerShield" to current.spoilerShield,
                        "autoplay" to current.autoplay,
                        "mature" to current.mature,
                        "matureBlur" to current.matureBlur,
                        "omdbKey" to current.omdbKey,
                        "hidePosterCaptions" to !current.posterCaptions,
                        "detailOrder" to current.detailOrder,
                        "_updatedAt" to System.currentTimeMillis(),
                    )
                ),
                SetOptions.merge(),
            ).await()
        }
    }
}
