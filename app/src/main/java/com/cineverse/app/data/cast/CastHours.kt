package com.cineverse.app.data.cast

import android.content.Context
import androidx.compose.runtime.Immutable
import com.cineverse.app.data.model.ShowProgress
import com.cineverse.app.data.tmdb.TmdbRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * "You've now watched 30 hours of Adam Scott", after the website's
 * js/cast-hours.js.
 *
 * Per show, each cast member's share is the episodes they appear in, scaled by
 * how much of the show you have watched, times the show's episode length. TMDB
 * does not say WHICH episodes a person is in, so it is an estimate - and it is
 * labelled as one, the way the website labels its own.
 */
@Immutable
data class CastPerson(
    val id: Int,
    val name: String,
    val profile: String?,
    val minutes: Int,
    val shows: List<String>,
) {
    val hours: Int get() = minutes / 60
}

@Immutable
data class CastHours(
    val people: List<CastPerson>,
    /** Milestones crossed since the last time this was worked out, newest first. */
    val crossed: List<Pair<CastPerson, Int>>,
)

class CastHoursRepository(context: Context, private val tmdb: TmdbRepository) {

    // Versioned: when the rules for who counts change, the ledger starts again
    // SILENTLY, rather than celebrating everyone the new rules let in.
    private val prefs = context.getSharedPreferences("cast_hours_v2", Context.MODE_PRIVATE)

    suspend fun compute(
        shows: Collection<ShowProgress>,
        /** Show ids TMDB files under Animation, from the library's genres. */
        animated: Set<Int>,
        limitShows: Int = 20,
    ): CastHours {
        // On screen, not in the booth. A long-running animated show credits
        // its voice cast in every episode, so counting them turned a thousand
        // episodes of Doraemon into "1,472 hours of Katsuhisa Houki" and pushed
        // every actor anyone had actually seen off the list.
        val ranked = shows.filter { it.watchedCount > 0 && it.totalEpisodes > 0 && it.tmdbId !in animated }
            .sortedByDescending { it.minutesWatched }
            .take(limitShows)
        val gate = Semaphore(3)
        val perShow = coroutineScope {
            ranked.map { show -> async { gate.withPermit { show to tmdb.tvCast(show.tmdbId) } } }.awaitAll()
        }
        val people = HashMap<Int, CastPerson>()
        for ((show, cast) in perShow) {
            val share = show.watchedCount.toDouble() / show.totalEpisodes
            val runtime = if (show.episodeRuntime > 0) show.episodeRuntime else 42
            for (member in cast.filter { it.id > 0 && it.totalEpisodeCount > 0 }) {
                val episodes = minOf(member.totalEpisodeCount, show.totalEpisodes)
                val minutes = (episodes * share * runtime).toInt()
                if (minutes <= 0) continue
                val held = people[member.id]
                people[member.id] = CastPerson(
                    id = member.id,
                    name = member.name,
                    profile = member.profilePath ?: held?.profile,
                    minutes = (held?.minutes ?: 0) + minutes,
                    shows = (held?.shows.orEmpty() + show.title).distinct(),
                )
            }
        }
        val top = people.values.sortedByDescending { it.minutes }.take(12)
        return CastHours(top, milestones(top))
    }

    /**
     * Who passed a milestone since the last look. The very first look records
     * everyone silently, so opening Stats for the first time is not twenty
     * celebrations of hours watched years ago.
     */
    private fun milestones(people: List<CastPerson>): List<Pair<CastPerson, Int>> {
        val first = !prefs.getBoolean("seeded", false)
        val crossed = mutableListOf<Pair<CastPerson, Int>>()
        val edit = prefs.edit()
        for (person in people) {
            val reached = MILESTONES.lastOrNull { person.hours >= it } ?: 0
            val before = prefs.getInt("p${person.id}", 0)
            if (reached > before) {
                if (!first) crossed += person to reached
                edit.putInt("p${person.id}", reached)
            }
        }
        edit.putBoolean("seeded", true).apply()
        return crossed.sortedByDescending { it.second }
    }

    companion object {
        val MILESTONES = listOf(5, 10, 20, 30, 40, 50, 75, 100, 150, 200, 250, 300, 400, 500, 750, 1000)
    }
}
