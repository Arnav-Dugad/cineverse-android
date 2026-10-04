package com.cineverse.app.feature.person

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.PosterCard
import com.cineverse.app.core.ui.rememberArrival
import com.cineverse.app.data.model.CreditedItem
import com.cineverse.app.data.model.MediaItem
import java.time.LocalDate

/** A career, decade by decade. */
object Timeline {
    /** 0 stands for "not out yet": titles without a date, or dated in the future. */
    const val UPCOMING = 0

    /** Newest decade first, upcoming before all of them; newest title first within each. */
    fun byDecade(credits: List<CreditedItem>, today: LocalDate = LocalDate.now()): List<Pair<Int, List<CreditedItem>>> {
        val year = today.year
        return credits
            .groupBy { credit ->
                val date = runCatching { LocalDate.parse(credit.item.releaseDate) }.getOrNull()
                when {
                    date == null || date.isAfter(today) -> UPCOMING
                    else -> date.year / 10 * 10
                }
            }
            .map { (decade, entries) -> decade to entries.sortedByDescending { it.item.releaseDate } }
            .sortedByDescending { (decade, _) -> if (decade == UPCOMING) year + 100 else decade }
    }
}

/**
 * One decade of a career on the timeline: a dot on the rail with the decade
 * beside it, how much of it you have seen, and its titles in a row. Each
 * decade rises in a beat after the one above it, so the career reads from
 * now backwards as it arrives.
 */
@Composable
internal fun DecadeBlock(
    decade: Int,
    credits: List<CreditedItem>,
    last: Boolean,
    index: Int,
    isWatched: (MediaItem) -> Boolean,
    isSaved: (MediaItem) -> Boolean,
    onOpen: (MediaItem) -> Unit,
) {
    val colors = CvTheme.colors
    val seen = credits.count { isWatched(it.item) }
    val arrive = rememberArrival(1f, (index * 90).coerceAtMost(450), 600)
    val accent = if (decade == Timeline.UPCOMING) Palette.Cyan2 else Palette.Red2
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = arrive; translationY = (1f - arrive) * 28f }
            // The rail: a dot for the decade, and the line on down to the next.
            // Drawn behind the row rather than measured into it: the row holds
            // a lazy list, which cannot be asked for an intrinsic height.
            .drawBehind {
                val x = 11.dp.toPx()
                val dotY = 14.dp.toPx()
                drawLine(
                    color = colors.text.copy(alpha = 0.14f),
                    start = Offset(x, dotY),
                    end = Offset(x, if (last) dotY else size.height),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = if (decade == Timeline.UPCOMING) PathEffect.dashPathEffect(floatArrayOf(8f, 8f)) else null,
                )
                drawCircle(accent.copy(alpha = 0.25f), radius = 9.dp.toPx() * arrive, center = Offset(x, dotY))
                drawCircle(accent, radius = 5.dp.toPx(), center = Offset(x, dotY))
            },
    ) {
        Spacer(Modifier.width(22.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(bottom = 22.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                Text(
                    if (decade == Timeline.UPCOMING) "Coming up" else "${decade}s",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.text,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    buildString {
                        append("${credits.size} title${if (credits.size == 1) "" else "s"}")
                        if (seen > 0) append(" · $seen seen")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.text3,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(end = 18.dp),
            ) {
                items(credits, key = { it.item.key + it.role }) { credit ->
                    Column(Modifier.width(104.dp)) {
                        PosterCard(
                            item = credit.item,
                            onOpen = onOpen,
                            width = 104.dp,
                            watched = isWatched(credit.item),
                            saved = isSaved(credit.item),
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            listOfNotNull(credit.item.year.ifBlank { null }, credit.role.ifBlank { null }).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.text3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
