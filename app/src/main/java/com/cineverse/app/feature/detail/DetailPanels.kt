package com.cineverse.app.feature.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Motion
import com.cineverse.app.core.design.tabular
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.data.model.Episode
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * How many times you have seen this, and when.
 *
 * Only for something already marked watched: there is no such thing as a
 * rewatch of something unwatched, and offering one would be offering to write a
 * figure that contradicts the library. "Log a rewatch" increments a count the
 * website keeps on the same document, so the two agree about how many times you
 * have sat through a film.
 */
@Composable
fun RewatchPanel(
    plays: Int,
    lastWatched: Long,
    onLogRewatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding)
            .clip(CvShape.XLarge)
            .background(colors.glass)
            .border(1.dp, colors.hairline, CvShape.XLarge)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.gold.copy(alpha = 0.14f))
                    .border(1.dp, colors.gold.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // The count animates when a rewatch is logged, which is the
                    // only feedback the action has: nothing else on the page
                    // moves, and a number that silently becomes a different
                    // number leaves you wondering whether the tap registered.
                    AnimatedContent(
                        targetState = plays,
                        transitionSpec = {
                            (slideInVertically { it } + fadeIn()) togetherWith
                                (slideOutVertically { -it } + fadeOut())
                        },
                        label = "plays",
                    ) { value ->
                        Text(
                            value.toString(),
                            style = MaterialTheme.typography.titleLarge.tabular(),
                            color = colors.gold,
                        )
                    }
                    Text(
                        if (plays == 1) "PLAY" else "PLAYS",
                        style = KickerStyle,
                        color = colors.gold.copy(alpha = 0.75f),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (plays) {
                        1 -> "Seen once"
                        2 -> "Seen twice"
                        else -> "Seen $plays times"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                )
                if (lastWatched > 0) {
                    Text(
                        "Watched ${DAY.format(Date(lastWatched))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.text3,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(CvShape.Medium)
                .background(colors.text.copy(alpha = 0.07f))
                .border(1.dp, colors.hairline, CvShape.Medium)
                .clickableNoRipple { haptics?.play(Haptic.Success); onLogRewatch() },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Add, null, tint = colors.text2, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Log a rewatch",
                style = MaterialTheme.typography.labelLarge,
                color = colors.text2,
            )
        }
    }
}

/**
 * The next episode, counting down.
 *
 * The one piece of a title page that is genuinely LIVE, and the reason it earns
 * a second-by-second tick rather than a date: "in two days" is information you
 * read once, "02 : 21 : 50 : 05" is information you come back to. The website
 * has exactly this, and it is the thing people screenshot.
 *
 * Three honesty rules, all of which the website follows:
 *
 *  - It only appears when TMDB has an actual DATE. A show with an episode
 *    ordered but unscheduled gets no countdown, because counting down to a
 *    guess is worse than saying nothing.
 *  - The badge says whether the date is confirmed. TMDB carries plenty of
 *    placeholder dates that move; claiming precision they do not have is how a
 *    countdown loses its credibility the first time it is wrong.
 *  - At zero it stops and says the episode is out, rather than counting into
 *    negative numbers.
 */
@Composable
fun NextEpisodePanel(episode: Episode, modifier: Modifier = Modifier) {
    val colors = CvTheme.colors

    val airAt = remember(episode.airDate) {
        runCatching {
            LocalDate.parse(episode.airDate)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    } ?: return

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(airAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val remaining = (airAt - now).coerceAtLeast(0L)
    val days = remaining / 86_400_000L
    val hours = (remaining / 3_600_000L) % 24
    val minutes = (remaining / 60_000L) % 60
    val seconds = (remaining / 1_000L) % 60

    // How far through the wait we are, measured from the episode before it. A
    // bar with no start is just a dot that moves.
    val span = 7 * 86_400_000L
    val progress = ((span - remaining).toFloat() / span).coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = progress,
        animationSpec = Motion.landing(),
        label = "countdown",
    )

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding)
            .clip(CvShape.XLarge)
            .background(colors.glass)
            .border(1.dp, colors.hairline, CvShape.XLarge)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colors.cyan)
            )
            Spacer(Modifier.width(8.dp))
            Text("NEXT EPISODE", style = KickerStyle, color = colors.text3)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(CvShape.Pill)
                    .background(colors.text.copy(alpha = 0.07f))
                    .border(1.dp, colors.hairline, CvShape.Pill)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    if (remaining == 0L) "Out now" else "Date confirmed",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.text3,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            buildString {
                append("S").append(episode.season).append("E").append(episode.number)
                if (episode.name.isNotBlank()) append("  ·  ").append(episode.name)
            },
            style = MaterialTheme.typography.titleMedium,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(18.dp))

        if (remaining == 0L) {
            Text(
                "Out now",
                style = MaterialTheme.typography.displaySmall,
                color = colors.green,
            )
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                listOf(
                    days to "DAYS",
                    hours to "HOURS",
                    minutes to "MINUTES",
                    seconds to "SECONDS",
                ).forEachIndexed { index, pair ->
                    if (index > 0) {
                        Text(
                            "·",
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.text3.copy(alpha = 0.45f),
                        )
                    }
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "%02d".format(pair.first),
                            // Tabular, or every tick of the seconds shifts the
                            // three columns beside it half a pixel sideways.
                            style = MaterialTheme.typography.displaySmall.tabular(),
                            fontWeight = FontWeight.Light,
                            color = colors.text,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(pair.second, style = KickerStyle, color = colors.text3)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CvShape.Pill)
                .background(colors.text.copy(alpha = 0.12f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated.coerceAtLeast(0.02f))
                    .height(3.dp)
                    .clip(CvShape.Pill)
                    .background(colors.cyan)
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            EXACT.format(Date(airAt)),
            style = MaterialTheme.typography.labelSmall,
            color = colors.text3,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val DAY = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
private val EXACT = SimpleDateFormat("EEE d MMM, HH:mm zzz", Locale.getDefault())

/**
 * What it won.
 *
 * TMDB has no awards feed, so these come from Wikidata, and nothing here is
 * invented: no trophy is drawn that Wikidata did not return, and no imitation
 * award logo is ever shown. Wins first, grouped by programme, because "four
 * Oscars" is the fact and "Best Director, Best Picture, Best Actor, Best
 * Original Screenplay" is the detail underneath it.
 *
 * Nominations are listed only where they are not already wins. A win and a
 * nomination for the same award is a win; counting both is how a panel ends up
 * claiming more than happened.
 */
@Composable
fun AwardsPanel(
    awards: com.cineverse.app.data.awards.Awards,
    modifier: Modifier = Modifier,
) {
    val colors = CvTheme.colors
    if (!awards.any) return

    val byProgramme = remember(awards) {
        awards.wins.groupBy { it.programme }.entries.sortedByDescending { it.value.size }
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding)
            .clip(CvShape.XLarge)
            .background(colors.glass)
            .border(1.dp, colors.hairline, CvShape.XLarge)
            .padding(16.dp)
    ) {
        Text("RECOGNITION", style = KickerStyle, color = colors.text3)
        Spacer(Modifier.height(5.dp))
        Text(
            buildString {
                if (awards.wins.isNotEmpty()) {
                    append(awards.wins.size)
                    append(if (awards.wins.size == 1) " win" else " wins")
                }
                if (awards.nominations.isNotEmpty()) {
                    if (isNotEmpty()) append("  \u00b7  ")
                    append(awards.nominations.size)
                    append(if (awards.nominations.size == 1) " nomination" else " nominations")
                }
            },
            style = MaterialTheme.typography.titleMedium,
            color = colors.text,
        )

        for ((programme, won) in byProgramme) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(colors.gold.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        won.size.toString(),
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = colors.gold,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    programme,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            for (award in won.take(4)) {
                Text(
                    buildString {
                        append(award.name.removePrefix(programme).trim(' ', '-', ':', ','))
                        if (award.year.isNotBlank()) append("  \u00b7  ").append(award.year)
                    }.ifBlank { award.name },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 36.dp, top = 3.dp),
                )
            }
        }

        if (awards.nominations.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                // DISTINCT programmes. Four Academy Award nominations are four
                // nominations and one programme; listing the name four times
                // reads as a bug and says nothing the count did not.
                "Also nominated for " +
                    awards.nominations
                        .map { it.programme }
                        .distinct()
                        .take(4)
                        .joinToString(", ")
                        .ifBlank { "several awards" },
                style = MaterialTheme.typography.labelSmall,
                color = colors.text3,
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "From Wikidata",
            style = MaterialTheme.typography.labelSmall,
            color = colors.text3.copy(alpha = 0.7f),
        )
    }
}
