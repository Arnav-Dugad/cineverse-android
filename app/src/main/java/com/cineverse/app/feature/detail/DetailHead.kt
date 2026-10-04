package com.cineverse.app.feature.detail

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.drawWithContent
import com.cineverse.app.core.ui.geminiGlow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.ui.graphics.luminance
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Motion
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.glass
import com.cineverse.app.core.ui.AnimatedBookmark
import com.cineverse.app.core.ui.AnimatedCheck
import com.cineverse.app.core.ui.AnimatedStar
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.TonedLogo
import com.cineverse.app.core.ui.Img
import com.cineverse.app.core.ui.ScoreRow
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.core.ui.sharedPoster
import com.cineverse.app.data.model.TitleDetail
import com.cineverse.app.data.scores.Scores

/**
 * The top of a title page.
 *
 * Rebuilt to the shape the website settled on, which is centred rather than
 * left-aligned: poster floating over the artwork, the title treatment under it,
 * the tagline, then every piece of meta — our score, IMDb, the year, the
 * certificate, the genres — as ONE wrapped row of chips rather than three
 * separate lines in three separate styles.
 *
 * That last part is the whole idea. The old head had the score pills on one
 * row, the genre chips on another and the year buried in a dot-separated string
 * in a third; all three say "here is a fact about this title" and saying it
 * three ways made the block taller and harder to scan. One chip style, one
 * flow, wrapping where it needs to.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailHead(
    detail: TitleDetail,
    scores: Scores,
    saved: Boolean,
    watched: Boolean,
    myRating: Int,
    movieMinutes: Int,
    /** Off shows the typeset name instead of the title's own treatment. */
    showTitleLogo: Boolean,
    /** No title at all, logo or name: the poster and artwork say it. */
    hideTitle: Boolean = false,
    onSave: () -> Unit,
    onWatched: () -> Unit,
    onRate: () -> Unit,
    onShare: () -> Unit,
    onPlayTrailer: () -> Unit,
    onLists: () -> Unit,
    onProgress: () -> Unit,
    /** Ask Gemini about it, spoiler-safe. */
    /** Null with Gemini switched off: no pill at all. */
    onAsk: (() -> Unit)? = null,
    /** Films only: start the "watching now" Live Update. Null hides it. */
    onWatchingNow: (() -> Unit)? = null,
    /** True while that Live Update is running for this film. */
    watchingNow: Boolean = false,
) {
    val accent = com.cineverse.app.core.ui.LocalTitleAccent.current
    // A sand or yellow accent would make white type unreadable on the button.
    val onAccent = if (accent != null && accent.luminance() > 0.5f) Color(0xFF0B0B10) else Color.White
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current

    Column(
        Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The poster OVERLAPS the artwork, which is what makes the top of the
        // page read as one object rather than a picture with a card underneath
        // it.
        //
        // See [risingPoster]: the poster rises into the artwork and takes up
        // only the room it shows below it, so whatever follows sits right
        // under its bottom edge.
        Box(
            Modifier
                .risingPoster()
                .sharedPoster(detail.key)
                .clip(CvShape.Large)
                .border(1.dp, colors.text.copy(alpha = 0.16f), CvShape.Large)
        ) {
            CvImage(
                Img.posterLarge(detail.posterPath),
                detail.title,
                Modifier.requiredSize(width = POSTER_WIDTH, height = POSTER_HEIGHT),
            )
        }

        if (hideTitle) {
            // Nothing: the poster above already says what this is. The name is
            // still read out to accessibility services.
            Spacer(Modifier.height(8.dp).semantics { heading(); contentDescription = detail.title })
        } else if (detail.logoPath != null && showTitleLogo) {
            Spacer(Modifier.height(18.dp))
            TonedLogo(
                detail.logoPath,
                detail.title,
                Modifier.fillMaxWidth(0.86f).height(66.dp),
                align = Alignment.Center,
            )
        } else {
            Spacer(Modifier.height(16.dp))
            Text(
                detail.title,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.text,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (detail.tagline.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "“${detail.tagline}”",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text3,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Everything factual, in one flow.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (detail.voteAverage > 0) {
                Chip(tint = colors.gold) {
                    Icon(
                        Icons.Rounded.Star,
                        null,
                        tint = colors.gold,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        String.format("%.1f", detail.voteAverage),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.gold,
                    )
                }
            }
            // The outside scores keep their own marks, which are the whole
            // point of them, so they are placed as-is inside the same flow.
            if (scores.any) ScoreRow(scores)

            detail.year.takeIf { it.isNotBlank() }?.let { Chip { PlainText(it) } }
            detail.certificate.takeIf { it.isNotBlank() }?.let { Chip { PlainText(it) } }
            runtimeLabelFor(detail)?.let { Chip { PlainText(it) } }
            for (genre in detail.genres.take(4)) {
                Chip { PlainText(genre.name) }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ONE line, always.
        //
        // The wrapping version pushed Share onto a second row by itself, which
        // looked like a mistake rather than a layout. The trailer button takes
        // the remaining width instead, so the five targets fit whatever the
        // phone is: the button shrinks, the circles never do, because a 44dp
        // target is the floor below which a thumb starts missing.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (detail.trailer != null) {
                Row(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(CvShape.Pill)
                        .background(
                            Brush.horizontalGradient(
                                accent?.let { listOf(it.copy(alpha = 0.92f), it) } ?: listOf(Palette.Red, Palette.Red2)
                            )
                        )
                        .clickableNoRipple { haptics?.play(Haptic.Tap); onPlayTrailer() }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        null,
                        tint = onAccent,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "Trailer",
                        style = MaterialTheme.typography.labelLarge,
                        color = onAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Drawn icons rather than glyphs: the bookmark turns out of a plus,
            // the tick draws itself, the star fills from the middle. Each one
            // springs from wherever it currently is, so a fast double tap
            // carries on rather than snapping back to the start.
            ActionCircle(
                active = saved,
                description = if (saved) "In your list" else "Add to your list",
                onLongPress = { haptics?.play(Haptic.Peek); onLists() },
                onClick = { haptics?.play(if (saved) Haptic.Untick else Haptic.Tick); onSave() },
            ) { tint -> AnimatedBookmark(saved, tint) }

            ActionCircle(
                active = watched,
                activeTint = colors.green,
                description = if (watched) "Watched" else "Mark watched",
                onClick = { haptics?.play(if (watched) Haptic.Untick else Haptic.Tick); onWatched() },
            ) { tint -> AnimatedCheck(watched, tint) }

            ActionCircle(
                active = myRating > 0,
                activeTint = colors.gold,
                label = if (myRating > 0) myRating.toString() else null,
                description = "Rate",
                onClick = { haptics?.play(Haptic.Tap); onRate() },
            ) { tint -> AnimatedStar(myRating > 0, tint) }

            ActionCircle(
                active = false,
                description = "Share",
                onClick = { haptics?.play(Haptic.Tap); onShare() },
            ) { tint ->
                Icon(
                    Icons.Rounded.Share,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(21.dp),
                )
            }
        }

        if (!detail.isSeries && detail.runtime > 0 && (movieMinutes > 0 || !watched)) {
            Spacer(Modifier.height(16.dp))
            MovieProgressStrip(
                minutes = movieMinutes,
                runtime = detail.runtime,
                onClick = { haptics?.play(Haptic.Tap); onProgress() },
            )
        }

        if (onAsk != null || onWatchingNow != null) Spacer(Modifier.height(14.dp))
        if (onAsk != null || onWatchingNow != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onAsk != null) HeadPill(
                text = "Ask about it",
                icon = Icons.Rounded.AutoAwesome,
                gemini = true,
                modifier = Modifier.weight(1f),
            ) { haptics?.play(Haptic.Tap); onAsk() }
            if (onWatchingNow != null) {
                HeadPill(
                    text = if (watchingNow) "Watching now" else "Start watching",
                    icon = if (watchingNow) Icons.Rounded.Stop else Icons.Rounded.PlayCircle,
                    live = watchingNow,
                    modifier = Modifier.weight(1f),
                ) { haptics?.play(if (watchingNow) Haptic.Untick else Haptic.Tick); onWatchingNow() }
            }
        }

        if (detail.overview.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            ExpandableText(detail.overview)
        }
    }
}

/** A secondary action under the main row: Ask, Watching now. */
@Composable
private fun HeadPill(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    gemini: Boolean = false,
    live: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = CvTheme.colors
    val pulse = if (live) {
        val loop = androidx.compose.animation.core.rememberInfiniteTransition(label = "livePulse")
        loop.animateFloat(
            0.35f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(900),
                androidx.compose.animation.core.RepeatMode.Reverse,
            ),
            label = "livePulseValue",
        ).value
    } else 1f
    Row(
        modifier
            .height(42.dp)
            .then(if (gemini) Modifier.geminiGlow(corner = 21.dp, width = 1.2.dp) else Modifier)
            .clip(CvShape.Pill)
            .background(if (live) Palette.Red2.copy(alpha = 0.16f) else colors.glass)
            .clickableNoRipple(onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (gemini) {
            Icon(
                icon, null,
                modifier = Modifier
                    .size(17.dp)
                    .graphicsLayer(compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawRect(com.cineverse.app.core.ui.geminiBrushStatic, blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
                    },
            )
        } else {
            if (live) {
                Box(Modifier.size(8.dp).graphicsLayer { alpha = pulse }.clip(CircleShape).background(Palette.Red2))
                Spacer(Modifier.width(7.dp))
            } else {
                Icon(icon, null, tint = colors.text2, modifier = Modifier.size(17.dp))
            }
        }
        Spacer(Modifier.width(7.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = colors.text, maxLines = 1)
    }
}

/**
 * The one-line read on a film you paused.
 *
 * It states the time rather than only drawing a bar, because "48m left" is the
 * thing being decided and a bar at 62% is not.
 */
@Composable
private fun MovieProgressStrip(minutes: Int, runtime: Int, onClick: () -> Unit) {
    val colors = CvTheme.colors
    val started = minutes > 0
    val fraction = (minutes.toFloat() / runtime).coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = Motion.landing(),
        label = "filmProgress",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .glass(CvShape.Medium)
            .clickableNoRipple(onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (started) "${runtime - minutes}m left" else "Set your place",
                style = MaterialTheme.typography.labelLarge,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (started) "${(fraction * 100).toInt()}%" else "Not started",
                style = MaterialTheme.typography.labelMedium,
                color = colors.text3,
            )
        }
        if (started) {
            Spacer(Modifier.height(9.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CvShape.Pill)
                    .background(colors.text.copy(alpha = 0.14f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(animated)
                        .height(4.dp)
                        .clip(CvShape.Pill)
                        .background(Palette.Red2)
                )
            }
        }
    }
}

/** One meta chip. Every fact on the page wears this, so none shouts over another. */
@Composable
private fun Chip(
    tint: Color = Color.Unspecified,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val colors = CvTheme.colors
    val fill = if (tint == Color.Unspecified) colors.glass else tint.copy(alpha = 0.16f)
    val edge = if (tint == Color.Unspecified) colors.hairline else tint.copy(alpha = 0.35f)
    Row(
        Modifier
            .height(32.dp)
            .clip(CvShape.Pill)
            .background(fill)
            .border(1.dp, edge, CvShape.Pill)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun PlainText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = CvTheme.colors.text2,
        maxLines = 1,
    )
}

@Composable
private fun ActionCircle(
    active: Boolean,
    description: String,
    onClick: () -> Unit,
    activeTint: Color = Color.Unspecified,
    label: String? = null,
    onLongPress: (() -> Unit)? = null,
    icon: @Composable (Color) -> Unit,
) {
    val colors = CvTheme.colors
    val tint = when {
        !active -> colors.text
        activeTint != Color.Unspecified -> activeTint
        else -> Palette.Red2
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = Motion.snappy(),
        label = "action",
    )
    Box(
        Modifier
            .size(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (active) tint.copy(alpha = 0.14f) else colors.glass)
            .border(1.dp, if (active) tint.copy(alpha = 0.5f) else colors.hairline, CircleShape)
            .then(
                if (onLongPress == null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier.pointerInput(onClick, onLongPress) {
                        detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() })
                    }
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = tint)
        } else {
            icon(tint)
        }
    }
}

internal val POSTER_WIDTH = 172.dp
internal val POSTER_HEIGHT = 258.dp

/** How far the poster reaches up into the artwork, above its own slot. */
private val POSTER_RISE = 156.dp

/**
 * The title page's poster: full size, drawn [POSTER_RISE] up into the
 * artwork, while the slot it leaves in the page is only the part below that.
 *
 * Two earlier versions each left a gap under it. An offset moves the poster
 * and leaves its full-height slot behind; a short Box around a
 * `requiredSize` poster CENTRES the oversized poster in the Box, so half the
 * overlap came back as empty space beneath it - the gap between the poster
 * and the tagline once the title stopped filling it. Placing it by hand is
 * the only version that means exactly what it says.
 */
internal fun Modifier.risingPoster(): Modifier = layout { measurable, _ ->
    val poster = measurable.measure(
        androidx.compose.ui.unit.Constraints.fixed(POSTER_WIDTH.roundToPx(), POSTER_HEIGHT.roundToPx())
    )
    val rise = POSTER_RISE.roundToPx()
    layout(poster.width, poster.height - rise) { poster.place(0, -rise) }
}

private fun runtimeLabelFor(detail: TitleDetail): String? = when {
    detail.isSeries && detail.numberOfSeasons > 0 ->
        "${detail.numberOfSeasons} season${if (detail.numberOfSeasons == 1) "" else "s"}"
    detail.runtime > 0 -> "${detail.runtime / 60}h ${detail.runtime % 60}m"
    else -> null
}
