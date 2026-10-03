package com.cineverse.app.feature.person

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.tabular
import com.cineverse.app.core.ui.PosterRail
import com.cineverse.app.core.ui.ProgressRing
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.bleed
import com.cineverse.app.core.ui.glass
import com.cineverse.app.data.model.CreditedItem
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.model.PersonDetail
import java.time.LocalDate

/**
 * "You've seen 8 of 12 Christopher Nolan films", ported from the website's
 * js/completionist.js — with the films you have not seen listed best-rated
 * first, which is the actual use of the number.
 *
 * What counts as one of someone's films, so the count means something:
 *  - a feature released on or before today (an announced film cannot be a gap);
 *  - with at least 50 TMDB votes, the point where a film is established enough
 *    to count against you. A film you HAVE seen always counts, however obscure;
 *  - directors: credits with the job "Director";
 *  - actors: acting roles only — appearances as themselves and uncredited
 *    cameos are left out.
 */
@Immutable
data class Completion(
    val name: String,
    val role: Role,
    val seen: Int,
    val total: Int,
    /** Unseen films, highest rated first. */
    val gaps: List<MediaItem>,
) {
    enum class Role { Director, Actor }

    val fraction: Float get() = if (total > 0) seen.toFloat() / total else 0f
    val percent: Int get() = (fraction * 100).toInt()

    val headline: String
        get() {
            val noun = if (total == 1) "film" else "films"
            return if (seen >= total) "You've seen all $total $name $noun"
            else "You've seen $seen of $total $name $noun"
        }

    companion object {
        const val MIN_VOTES = 50
        private val SelfRole = Regex(
            """\b(self|himself|herself|themselves|narrator \(archive|archive footage)\b""",
            RegexOption.IGNORE_CASE,
        )
        private val Uncredited = Regex("uncredited", RegexOption.IGNORE_CASE)

        /** Which role a person page should measure: their known-for department decides. */
        fun roleFor(department: String): Role = if (department == "Directing") Role.Director else Role.Actor

        fun of(person: PersonDetail, watched: Set<Int>, today: LocalDate = LocalDate.now()): Completion {
            val role = roleFor(person.knownFor)
            val source: List<CreditedItem> = when (role) {
                Role.Director -> person.asCrew.filter { it.role == "Director" }
                Role.Actor -> person.asCast.filterNot { SelfRole.containsMatchIn(it.role) || Uncredited.containsMatchIn(it.role) }
            }
            val films = source.map { it.item }
                .filter { it.type == MediaType.Movie && it.id > 0 }
                .distinctBy { it.id }
                .filter { film ->
                    val mine = film.id in watched
                    val released = runCatching { LocalDate.parse(film.releaseDate) }.getOrNull()
                        ?.let { !it.isAfter(today) } ?: false
                    mine || (released && film.voteCount >= MIN_VOTES)
                }
            val gaps = films.filterNot { it.id in watched }
                .sortedWith(
                    compareByDescending<MediaItem> { it.voteAverage }
                        .thenByDescending { it.voteCount }
                        .thenByDescending { it.releaseDate }
                )
                .take(12)
            return Completion(
                name = person.name,
                role = role,
                seen = films.count { it.id in watched },
                total = films.size,
                gaps = gaps,
            )
        }
    }
}

/** The panel on a person page. Nothing at all when there is nothing to measure. */
@Composable
fun CompletionPanel(
    completion: Completion,
    onOpen: (MediaItem) -> Unit,
    isSaved: (MediaItem) -> Boolean,
    modifier: Modifier = Modifier,
) {
    if (completion.total < 2) return
    val colors = CvTheme.colors
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .glass(CvShape.XLarge, strength = 0.7f)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressRing(completion.fraction, size = 64.dp, delayMillis = 250) {
                Text(
                    "${completion.percent}%",
                    style = MaterialTheme.typography.labelLarge.tabular(),
                    color = if (completion.seen >= completion.total) colors.gold else colors.text,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (completion.role == Completion.Role.Director) "AS DIRECTOR" else "ON SCREEN",
                    style = KickerStyle,
                    color = colors.text3,
                )
                Spacer(Modifier.height(3.dp))
                Text(completion.headline, style = MaterialTheme.typography.titleSmall, color = colors.text)
                if (completion.gaps.isNotEmpty()) {
                    Text(
                        "${completion.total - completion.seen} to go — the best-rated first, below",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.text3,
                    )
                }
            }
        }
        if (completion.gaps.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            PosterRail(
                items = completion.gaps,
                onOpen = onOpen,
                title = "Still to see",
                cardWidth = 108.dp,
                isSaved = isSaved,
                modifier = Modifier.bleed(ScreenPadding),
            )
        }
    }
}
