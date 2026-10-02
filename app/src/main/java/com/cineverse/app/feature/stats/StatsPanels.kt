package com.cineverse.app.feature.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.Motion
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.CountUpDecimal
import com.cineverse.app.core.ui.CountUpText
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.Img
import com.cineverse.app.core.ui.ScreenPadding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The panels under the headline figures.
 *
 * Each one is a card rather than a full-bleed section, because on a phone a
 * wall of edge-to-edge statistics has no rhythm to it — the eye needs to know
 * where one idea stops and the next starts, and on a page of twelve ideas that
 * is worth 16dp of margin apiece.
 */

@Composable
fun Panel(
    kicker: String,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors = CvTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding)
            .clip(CvShape.XLarge)
            .background(colors.glass)
            .border(1.dp, colors.hairline, CvShape.XLarge)
            .padding(16.dp)
    ) {
        Text(kicker.uppercase(), style = KickerStyle, color = colors.text3)
        Spacer(Modifier.height(5.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.text)
        Spacer(Modifier.height(14.dp))
        content()
    }
}

// ---------- rating intelligence ----------

@Composable
fun RatingPanel(intel: RatingIntel) {
    val colors = CvTheme.colors
    Panel("Your critical voice", "Rating intelligence") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                CountUpDecimal(
                    intel.average,
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.gold,
                )
                Text("average score", style = MaterialTheme.typography.labelSmall, color = colors.text3)
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    intel.personality,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.text,
                )
                Text(
                    "${intel.generous * 100 / intel.total.coerceAtLeast(1)}% of your ratings are 8 or higher",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.text3,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Ten columns, one per score. Heights are relative to the commonest
        // score rather than to ten, or a library where nothing is rated 1 would
        // show nine invisible bars.
        val peak = intel.distribution.maxOrNull()?.coerceAtLeast(1) ?: 1
        Row(
            Modifier.fillMaxWidth().height(76.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            intel.distribution.forEachIndexed { index, count ->
                val share by animateFloatAsState(
                    targetValue = count.toFloat() / peak,
                    animationSpec = Motion.landing(),
                    label = "dist",
                )
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height((56 * share).dp.coerceAtLeast(3.dp))
                            .clip(CvShape.Tiny)
                            .background(
                                if (count == 0) colors.text.copy(alpha = 0.08f)
                                else colors.gold.copy(alpha = 0.35f + 0.5f * share)
                            )
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.text3,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Footnotes(
            "${intel.total} rated",
            "${intel.coverage}% of what you have watched",
            "${intel.generous} favourites",
        )
    }
}

// ---------- the library ----------

@Composable
fun LibraryPanel(intel: LibraryIntel) {
    val colors = CvTheme.colors
    Panel("Collection", "Library anatomy") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ring(intel.completion / 100f, "${intel.completion}%")
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    "${intel.watched} watched",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.text,
                )
                Text(
                    "${intel.saved} still saved",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.text3,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        for ((label, value, note) in intel.facts) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.text3,
                    modifier = Modifier.width(84.dp),
                )
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(note, style = MaterialTheme.typography.labelSmall, color = colors.text3)
            }
        }
    }
}

// ---------- television ----------

@Composable
fun TvPanel(intel: TvIntel) {
    val colors = CvTheme.colors
    Panel("Episode intelligence", "TV tracker") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Tracked", intel.tracked.toString(), Modifier.weight(1f))
            Stat("Finished", intel.finished.toString(), Modifier.weight(1f))
            Stat("In flight", intel.inFlight.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Episodes", "%,d".format(intel.episodes), Modifier.weight(1f))
            Stat("Through", "${intel.completion}%", Modifier.weight(1f))
            Stat(
                "Biggest day",
                intel.biggestDay?.second?.toString() ?: "–",
                Modifier.weight(1f),
                note = intel.biggestDay?.first?.let { DAY_FORMAT.format(Date(it)) },
            )
        }

        if (intel.closest.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text(
                "CLOSEST TO FINISHING",
                style = KickerStyle,
                color = colors.text3,
            )
            Spacer(Modifier.height(10.dp))
            for (show in intel.closest) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(width = 34.dp, height = 50.dp)
                            .clip(CvShape.Small)
                    ) {
                        CvImage(Img.poster(show.poster.ifBlank { null }), show.title, Modifier.fillMaxWidth().fillMaxHeight())
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            show.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(5.dp))
                        Bar(show.fraction, colors.green)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${show.left} left",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.text3,
                    )
                }
            }
        }
    }
}

// ---------- taste ----------

@Composable
fun TastePanel(taste: TasteMap) {
    Panel("Across everything you keep", "Taste map") {
        SliceGroup("Decades", taste.decades, Palette.Red2)
        if (taste.languages.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SliceGroup("Languages", taste.languages, CvTheme.colors.cyan)
        }
        if (taste.countries.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SliceGroup("Countries", taste.countries, CvTheme.colors.purple)
        }
    }
}

@Composable
private fun SliceGroup(title: String, slices: List<Slice>, tint: Color) {
    val colors = CvTheme.colors
    if (slices.isEmpty()) return
    Text(title.uppercase(), style = KickerStyle, color = colors.text3)
    Spacer(Modifier.height(9.dp))
    for (slice in slices) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                slice.name,
                style = MaterialTheme.typography.labelMedium,
                color = colors.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(96.dp),
            )
            Box(Modifier.weight(1f)) { Bar(slice.share, tint) }
            Spacer(Modifier.width(10.dp))
            Text(
                slice.count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.text3,
            )
        }
    }
}

// ---------- rewatches ----------

@Composable
fun RewatchPanel(rewatches: List<Rewatch>) {
    val colors = CvTheme.colors
    Panel("What you go back to", "Rewatches") {
        for (item in rewatches) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(width = 34.dp, height = 50.dp).clip(CvShape.Small)) {
                    CvImage(Img.poster(item.poster.ifBlank { null }), item.title, Modifier.fillMaxWidth().fillMaxHeight())
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${item.plays} times through",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.text3,
                    )
                }
                Text(
                    "+${item.minutes / 60}h",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.gold,
                )
            }
        }
        Text(
            "Extra hours only — the first time through is counted in your total.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.text3,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// ---------- taste changes ----------

@Composable
fun ShiftPanel(shifts: List<TasteShift>) {
    val colors = CvTheme.colors
    Panel("Month by month", "Taste changes") {
        for (shift in shifts) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    shift.month,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.text3,
                    modifier = Modifier.width(66.dp),
                )
                Text(
                    shift.genre,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    shift.language,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.text3,
                )
            }
        }
    }
}

// ---------- collection health ----------

@Composable
fun HealthPanel(lines: List<HealthLine>) {
    val colors = CvTheme.colors
    val clean = lines.all { it.missing == 0 }
    Panel("Quality control", "Collection health") {
        if (clean) {
            Text(
                "Nothing missing. Every watched title has its artwork, dates, genres and credits.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.green,
            )
            return@Panel
        }
        for (line in lines) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    line.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.text2,
                    modifier = Modifier.width(96.dp),
                )
                Box(Modifier.weight(1f)) {
                    // The bar shows what is PRESENT, not what is missing: a long
                    // red bar reads as an alarm, and "92% complete" is the same
                    // fact told the way round that is actually useful.
                    Bar(1f - line.share, if (line.share > 0.25f) Palette.Red2 else colors.green)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (line.missing == 0) "all" else "${line.missing} missing",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.text3,
                )
            }
        }
    }
}

// ---------- people ----------

@Composable
fun DirectorPanel(directors: List<PersonCount>) {
    val colors = CvTheme.colors
    Panel("Who you watch", "Directors you return to") {
        for (person in directors) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    person.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${person.count} films",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.text3,
                )
            }
        }
    }
}

// ---------- franchises ----------

@Composable
fun FranchisePanel(franchises: List<Franchise>, onOpen: (Franchise) -> Unit) {
    Panel("Series you follow", "Franchises") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(franchises, key = { it.id }) { franchise ->
                Column(Modifier.width(86.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(129.dp)
                            .clip(CvShape.Medium)
                    ) {
                        CvImage(
                            Img.poster(franchise.poster.ifBlank { null }),
                            franchise.name,
                            Modifier.fillMaxWidth().fillMaxHeight(),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        franchise.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = CvTheme.colors.text2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${franchise.watched} seen",
                        style = MaterialTheme.typography.labelSmall,
                        color = CvTheme.colors.text3,
                    )
                }
            }
        }
    }
}

// ---------- trophies ----------

@Composable
fun TrophyPanel(trophies: List<Trophy>) {
    val colors = CvTheme.colors
    val earned = trophies.count { it.earned }
    Panel("Milestones", "$earned of ${trophies.size} earned") {
        for (trophy in trophies) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (trophy.earned) colors.gold.copy(alpha = 0.18f)
                            else colors.text.copy(alpha = 0.06f)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (trophy.earned) "★" else "${(trophy.progress * 100).toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (trophy.earned) colors.gold else colors.text3,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        trophy.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (trophy.earned) colors.text else colors.text2,
                        fontWeight = if (trophy.earned) FontWeight.Medium else FontWeight.Normal,
                    )
                    Spacer(Modifier.height(4.dp))
                    Bar(trophy.progress, if (trophy.earned) colors.gold else colors.text3)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    trophy.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.text3,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(96.dp),
                )
            }
        }
    }
}

// ---------- pieces ----------

@Composable
private fun Bar(fraction: Float, tint: Color) {
    val colors = CvTheme.colors
    val width by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = Motion.landing(),
        label = "bar",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(CvShape.Pill)
            .background(colors.text.copy(alpha = 0.1f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(width.coerceAtLeast(0.01f))
                .height(5.dp)
                .clip(CvShape.Pill)
                .background(tint)
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, note: String? = null) {
    val colors = CvTheme.colors
    Column(modifier) {
        Text(label.uppercase(), style = KickerStyle, color = colors.text3)
        Spacer(Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = colors.text, maxLines = 1)
        if (note != null) {
            Text(note, style = MaterialTheme.typography.labelSmall, color = colors.text3, maxLines = 1)
        }
    }
}

/** A progress ring, for the one figure on the page that deserves one. */
@Composable
private fun Ring(fraction: Float, label: String) {
    val colors = CvTheme.colors
    val swept by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = Motion.landing(),
        label = "ring",
    )
    Box(
        Modifier
            .size(62.dp)
            .drawBehind {
                val stroke = 6.dp.toPx()
                val inset = stroke / 2f
                drawArc(
                    color = colors.text.copy(alpha = 0.1f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                )
                drawArc(
                    color = colors.green,
                    startAngle = -90f,
                    sweepAngle = 360f * swept,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        stroke,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.text)
    }
}

@Composable
private fun Footnotes(vararg notes: String) {
    val colors = CvTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (note in notes) {
            Text(note, style = MaterialTheme.typography.labelSmall, color = colors.text3)
        }
    }
}

private val DAY_FORMAT = SimpleDateFormat("d MMM", Locale.getDefault())
