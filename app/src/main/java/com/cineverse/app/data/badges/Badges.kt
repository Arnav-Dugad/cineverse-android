package com.cineverse.app.data.badges

import android.content.Context
import androidx.compose.runtime.Immutable
import com.cineverse.app.data.diary.Diary
import com.cineverse.app.data.firebase.AuthRepository
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.model.ShowProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

enum class Tier { Bronze, Silver, Gold, Platinum }

enum class Difficulty(val label: String) { Easy("Easy"), Medium("Medium"), Hard("Hard"), Insane("Insane"), Legendary("Legendary") }

/** What every badge reads, counted in one pass over the library: the website's `buildCtx`. */
@Immutable
data class BadgeContext(
    val watched: Int,
    val watchlist: Int,
    val rated: Int,
    val perfect: Int,
    val hours: Int,
    val genres: Int,
    val decades: Int,
    val topDirector: Int,
    val topActor: Int,
) {
    companion object {
        fun of(library: Library): BadgeContext {
            val films = library.watched.values
            val directors = films.filter { it.type == MediaType.Movie && it.director.isNotBlank() }
                .groupingBy { it.director }.eachCount()
            val actors = films.flatMap { it.cast }.groupingBy { it.id }.eachCount()
            return BadgeContext(
                watched = films.size,
                watchlist = library.saved.size,
                rated = library.ratings.size,
                perfect = library.ratings.values.count { it == 10 },
                // The website's rule: each watched title's stored runtime.
                hours = films.sumOf { it.runtime.coerceAtLeast(0) } / 60,
                genres = films.flatMap { it.genres }.distinct().size,
                decades = films.mapNotNull { it.year.take(4).toIntOrNull() }.map { it / 10 }.distinct().size,
                topDirector = directors.values.maxOrNull() ?: 0,
                topActor = actors.values.maxOrNull() ?: 0,
            )
        }
    }
}

/** One badge, the website's: earned when its value reaches its goal, so progress and earning never disagree. */
@Immutable
data class Badge(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val tier: Tier,
    val goal: Int,
    val unit: String = "",
    val value: (BadgeContext) -> Int,
)

@Immutable
data class Challenge(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val difficulty: Difficulty,
    val goal: Int,
    val unit: String = "",
    val value: (BadgeContext) -> Int,
)

/** The website's registry, badge for badge, so the two show the same unlocks. */
object Badges {
    val ALL = listOf(
        Badge("first_watch", "First Steps", "Mark your first title as watched", "clapper", Tier.Bronze, 1) { it.watched },
        Badge("watch_10", "Getting Started", "Watch 25 titles", "popcorn", Tier.Bronze, 25) { it.watched },
        Badge("watch_50", "Cinephile", "Watch 100 titles", "film", Tier.Silver, 100) { it.watched },
        Badge("watch_100", "Veteran Viewer", "Watch 300 titles", "medal", Tier.Gold, 300) { it.watched },
        Badge("watch_250", "Living Archive", "Watch 750 titles", "columns", Tier.Platinum, 750) { it.watched },
        Badge("hours_24", "Weekend Binger", "Watch 50 hours of content", "hourglass", Tier.Bronze, 50, "h") { it.hours },
        Badge("hours_100", "Time Traveller", "Watch 250 hours of content", "stopwatch", Tier.Silver, 250, "h") { it.hours },
        Badge("hours_500", "Marathoner", "Watch 1,000 hours of content", "runner", Tier.Gold, 1000, "h") { it.hours },
        Badge("rate_1", "Critic in Training", "Rate your first title", "star", Tier.Bronze, 1) { it.rated },
        Badge("rate_25", "Sharp Eye", "Rate 50 titles", "eye", Tier.Silver, 50) { it.rated },
        Badge("rate_100", "Head Critic", "Rate 250 titles", "trophy", Tier.Gold, 250) { it.rated },
        Badge("rate_750", "Master Critic", "Rate 750 titles", "scales", Tier.Platinum, 750) { it.rated },
        Badge("perfect_10", "Masterpiece", "Give a title a perfect 10", "perfect", Tier.Silver, 1) { it.perfect },
        Badge("genre_5", "Explorer", "Watch 8 different genres", "compass", Tier.Bronze, 8) { it.genres },
        Badge("genre_12", "Omnivore", "Watch 15 different genres", "globe", Tier.Gold, 15) { it.genres },
        Badge("decade_5", "Time Capsule", "Watch titles from 7 different decades", "cassette", Tier.Silver, 7) { it.decades },
        Badge("director_5", "Director Devotee", "Watch 8 titles by one director", "camera", Tier.Silver, 8) { it.topDirector },
        Badge("director_20", "Auteur Loyalist", "Watch 20 titles by one director", "clapper", Tier.Platinum, 20) { it.topDirector },
        Badge("actor_10", "Fan Club", "Watch 20 titles with one actor", "starBurst", Tier.Gold, 20) { it.topActor },
        Badge("wl_25", "Curator", "Keep 50 titles in your watchlist", "clipboard", Tier.Bronze, 50) { it.watchlist },
    )

    val CHALLENGES = listOf(
        Challenge("c_start", "Getting Comfortable", "Watch 5 titles", "clapper", Difficulty.Easy, 5) { it.watched },
        Challenge("c_opinions", "First Opinions", "Rate 3 titles", "star", Difficulty.Easy, 3) { it.rated },
        Challenge("c_genres", "Genre Hopper", "Watch 12 different genres", "compass", Difficulty.Medium, 12) { it.genres },
        Challenge("c_hours100", "Time Traveller", "Watch 250 hours of content", "stopwatch", Difficulty.Medium, 250, "h") { it.hours },
        Challenge("c_decades", "Across the Ages", "Watch titles from 8 different decades", "cassette", Difficulty.Hard, 8) { it.decades },
        Challenge("c_cinephile", "The Cinephile", "Watch 250 titles", "film", Difficulty.Hard, 250) { it.watched },
        Challenge("c_director", "Auteur's Devotee", "Watch 15 titles by a single director", "camera", Difficulty.Hard, 15) { it.topDirector },
        Challenge("c_critic", "Completionist Critic", "Rate 500 titles", "trophy", Difficulty.Insane, 500) { it.rated },
        Challenge("c_perfectionist", "The Perfectionist", "Award 25 perfect 10s", "perfect", Difficulty.Insane, 25) { it.perfect },
        Challenge("c_archive", "Living Archive", "Watch 1,000 titles", "columns", Difficulty.Insane, 1000) { it.watched },
        Challenge("c_master", "Master Critic", "Rate 1,000 titles", "scales", Difficulty.Legendary, 1000) { it.rated },
        Challenge("c_hours1000", "Endless Hours", "Watch 2,500 hours of content", "hourglass", Difficulty.Legendary, 2500, "h") { it.hours },
    )

    fun earned(context: BadgeContext): Set<String> = ALL.filter { it.value(context) >= it.goal }.map { it.id }.toSet()
}

/** Something worth a moment of celebration. */
sealed interface Celebration {
    data class BadgeUnlocked(val badges: List<Badge>) : Celebration
    data class StreakMilestone(val days: Int) : Celebration
}

/**
 * Watches for new badges and streak milestones, and says so once.
 *
 * Both are DERIVED from the library, so the device only remembers what it has
 * already celebrated. The first look on a device seeds that memory silently -
 * nobody wants confetti for a backlog earned years ago - and a badge lost by
 * un-watching something is never celebrated twice. A streak milestone is the
 * website's: exactly 7, 30 or 100 days, today included, once per streak.
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
class Celebrations(
    private val context: Context,
    private val auth: AuthRepository,
    library: StateFlow<Library>,
    shows: StateFlow<Map<Int, ShowProgress>>,
    scope: CoroutineScope,
) {
    private val _events = MutableSharedFlow<Celebration>(extraBufferCapacity = 4)
    val events: SharedFlow<Celebration> = _events.asSharedFlow()

    private val prefs = context.getSharedPreferences("celebrations", Context.MODE_PRIVATE)
    private var primed = false

    init {
        combine(library, shows) { lib, progress -> lib to progress }
            .filter { it.first.loaded }
            .debounce(1_500)
            .onEach { (lib, progress) -> check(lib, progress) }
            .launchIn(scope)
    }

    private fun check(library: Library, shows: Map<Int, ShowProgress>) {
        val uid = auth.uid.value ?: return
        // Badges.
        val key = "badges_$uid"
        val earned = Badges.earned(BadgeContext.of(library))
        val ledger = prefs.getStringSet(key, null)
        if (ledger == null) {
            prefs.edit().putStringSet(key, earned).apply()
        } else {
            val fresh = earned - ledger
            if (fresh.isNotEmpty()) {
                prefs.edit().putStringSet(key, ledger + earned).apply()
                // The first check after a launch is the library arriving, not
                // you doing something: remembered, not celebrated.
                if (primed) _events.tryEmit(Celebration.BadgeUnlocked(Badges.ALL.filter { it.id in fresh }))
            }
        }
        // Streak milestones.
        val streakKey = "streaks_$uid"
        val days = Diary.days(Diary.entries(library, shows)).keys
        val streak = Diary.streak(days)
        val announced = prefs.getStringSet(streakKey, emptySet()).orEmpty()
        Diary.milestoneToday(streak, announced)?.let { milestone ->
            prefs.edit().putStringSet(streakKey, (announced + "${streak.start}:$milestone").toList().takeLast(20).toSet()).apply()
            _events.tryEmit(Celebration.StreakMilestone(milestone))
        }
        primed = true
    }
}
