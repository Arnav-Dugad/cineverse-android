package com.cineverse.app.data.cast

import androidx.compose.runtime.Immutable
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.model.PersonDetail
import com.cineverse.app.data.model.ShowProgress

/** Your time with one person on screen, and where it came from. */
@Immutable
data class ActorHours(
    val minutes: Int,
    val films: Int,
    val shows: Int,
    /** The titles that gave the most time, biggest first. */
    val top: List<Pair<String, Int>>,
) {
    val hours: Int get() = minutes / 60

    /** "34h", "50m", or "" for nothing. */
    val label: String
        get() = when {
            minutes >= 60 -> "${minutes / 60}h"
            minutes > 0 -> "${minutes}m"
            else -> ""
        }

    /** The website's hours club this reaches: 10, 25, 50, 100, 250, 500 or 1000; 0 below ten. */
    val club: Int get() = CLUBS.lastOrNull { hours >= it } ?: 0

    /** How far from this club to the next, 0..1; the ring fills by it. */
    val toNext: Float
        get() {
            val next = CLUBS.firstOrNull { hours < it } ?: return 1f
            val from = club
            return ((minutes / 60f - from) / (next - from)).coerceIn(0.04f, 1f)
        }

    companion object {
        val CLUBS = listOf(10, 25, 50, 100, 250, 500, 1000)

        /**
         * Each club's colour, the website's own: bronze, silver, gold, cyan,
         * violet, rose and orchid, deeper in a light theme so they still read.
         */
        fun clubColor(club: Int, dark: Boolean): Long = when (club) {
            10 -> if (dark) 0xFFD08B52 else 0xFFA15C24
            25 -> if (dark) 0xFFCBD5E1 else 0xFF64748B
            50 -> if (dark) 0xFFFBBF24 else 0xFFB7791F
            100 -> if (dark) 0xFF22D3EE else 0xFF0E7490
            250 -> if (dark) 0xFFA78BFA else 0xFF7C3AED
            500 -> if (dark) 0xFFFB7185 else 0xFFE11D48
            1000 -> if (dark) 0xFFF0ABFC else 0xFFC026D3
            // Under ten hours: a quiet slate, not yet a club.
            else -> if (dark) 0xFF64748B else 0xFF94A3B8
        }

        /**
         * Worked out from the person's acting credits and your library:
         *  - a film you have watched counts its runtime (each viewing);
         *  - a series counts the episodes they were in, scaled by how much of
         *    the show you have watched, at the show's episode length.
         * TMDB does not say which episodes a regular sits out, so a series is
         * an estimate, and the screens say so with an "≈".
         */
        fun of(person: PersonDetail, library: Library, shows: Map<Int, ShowProgress>): ActorHours {
            val parts = mutableListOf<Pair<String, Int>>()
            var films = 0
            var series = 0
            for (credit in person.asCast.distinctBy { it.item.key }) {
                val item = credit.item
                if (item.type == MediaType.Movie) {
                    val watched = library.watched[item.key] ?: continue
                    val runtime = watched.runtime.takeIf { it in 1..999 } ?: continue
                    parts += item.title to runtime
                    films++
                } else {
                    val progress = shows[item.id]
                    val total = progress?.totalEpisodes ?: 0
                    val inIt = credit.episodeCount.coerceAtLeast(1)
                    val minutes = when {
                        progress != null && progress.watchedCount > 0 && total > 0 -> {
                            val share = progress.watchedCount.toDouble() / total
                            val runtime = if (progress.episodeRuntime > 0) progress.episodeRuntime else 42
                            (minOf(inIt, total) * share * runtime).toInt()
                        }
                        // Marked watched as a whole, without episode ticks.
                        library.watched[item.key] != null -> {
                            val watched = library.watched[item.key]!!
                            val runtime = watched.episodeRuntime.takeIf { it > 0 } ?: 42
                            inIt * runtime
                        }
                        else -> 0
                    }
                    if (minutes <= 0) continue
                    parts += item.title to minutes
                    series++
                }
            }
            return ActorHours(
                minutes = parts.sumOf { it.second },
                films = films,
                shows = series,
                top = parts.sortedByDescending { it.second }.take(3),
            )
        }
    }
}
