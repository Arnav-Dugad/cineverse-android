package com.cineverse.app.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Velocity
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Motion
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.PaneEdge
import com.cineverse.app.core.ui.TabIcon
import com.cineverse.app.core.ui.glassPane

/**
 * The bottom bar hides on the way down and comes back on the way up.
 *
 * Browsing a rail of posters should get the whole screen; the bar is never more
 * than a flick away. The threshold is deliberately asymmetric — 24dp of
 * downward scroll to hide, 4dp of upward to show — because reaching for the bar
 * is an intentional act and losing it is not.
 */
/**
 * How the bars behave as the page moves under them.
 *
 * Not a boolean any more. The bar now has a CONTINUOUS position, driven
 * directly by the scroll, so it slides with the finger rather than snapping
 * between two states a moment after the gesture — the difference between a bar
 * that is attached to the page and one that is reacting to it.
 *
 * Three rules, each from watching a bar get this wrong:
 *
 *  - it NEVER hides near the top of a page, because hiding navigation on a
 *    page the user has barely moved reads as a glitch;
 *  - it settles to fully in or fully out when the finger lifts, so the gesture
 *    can never leave it stranded half way;
 *  - it comes back faster than it leaves. Reaching for navigation is urgent;
 *    getting it out of the way is not.
 */
class BarVisibility {
    /** 0 is fully shown, 1 is fully tucked away. Everything reads this. */
    var hidden by mutableFloatStateOf(0f)
        private set

    val visible: Boolean get() = hidden < 0.5f

    private var depth = 0f

    /** "Keep the navigation bar pinned": it never tucks away. */
    var pinned = false
        set(value) {
            val pinning = value && !field
            field = value
            if (pinning) show()
        }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (pinned) return Offset.Zero
            val dy = available.y
            depth = (depth - dy).coerceAtLeast(0f)

            // The first 140px of a page keep the bar, whatever the gesture.
            if (depth < 140f) {
                hidden = 0f
                return Offset.Zero
            }
            // Down the page: 90px of travel takes it out, 55px brings it back.
            val step = if (dy < 0) -dy / 90f else dy / 55f
            hidden = (if (dy < 0) hidden + step else hidden - step).coerceIn(0f, 1f)
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (pinned) return Velocity.Zero
            // Never stranded mid-slide. Past half it finishes hiding, otherwise
            // it comes back.
            hidden = if (hidden > 0.5f) 1f else 0f
            return Velocity.Zero
        }
    }

    fun show() {
        hidden = 0f
        depth = 0f
    }
}

@Composable
fun CvNavigationBar(
    current: Tab,
    bars: BarVisibility,
    onSelect: (Tab) -> Unit,
    savedCount: Int = 0,
    modifier: Modifier = Modifier,
    /**
     * The count the bar last acknowledged, held by the caller: the bar is not
     * composed over a title page, so a save made there is bumped on return.
     */
    acknowledgedSaved: Int = -1,
    onAcknowledgeSaved: (Int) -> Unit = {},
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    // Saving something bumps the My List tab: a spring that overshoots and
    // settles, so a save made anywhere is acknowledged by the place it went.
    val bump = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    val still = CvTheme.reducedMotion
    androidx.compose.runtime.LaunchedEffect(savedCount) {
        // Only a rise from a known count is a save; -1 means "not loaded yet".
        val grew = acknowledgedSaved >= 0 && savedCount > acknowledgedSaved
        onAcknowledgeSaved(savedCount)
        if (grew && !still) {
            // Let a returning page finish arriving before the tab reacts.
            kotlinx.coroutines.delay(220)
            bump.snapTo(1f)
            bump.animateTo(
                0f,
                androidx.compose.animation.core.spring(dampingRatio = 0.32f, stiffness = 260f),
            )
        }
    }
    // Driven by the scroll position rather than by a visibility flag, so the
    // bar tracks the finger. The spring only does the settling at the end of a
    // gesture, which is the one moment a spring belongs here.
    val tuck by animateFloatAsState(
        targetValue = if (CvTheme.reducedMotion) 0f else bars.hidden,
        animationSpec = Motion.snappy(),
        label = "bar",
    )
    Box(
        modifier.graphicsLayer {
            translationY = tuck * size.height
            // It fades as it goes, which stops the labels appearing to slide
            // underneath the page content on a light backdrop.
            alpha = 1f - tuck * 0.7f
        }
    ) {
        // The fade goes ABOVE the bar, not behind it.
        //
        // Two attempts got this wrong. A fixed-height Box left the bar floating
        // off the screen's edge; a gradient as the bar's own background left
        // poster captions legible straight through the labels, because the top
        // of that gradient is where the icons are. A row of text behind a row of
        // labels is the single ugliest thing a bottom bar can do, so the ramp
        // happens in its own strip and the bar itself is a glass pane holding
        // most of the page colour - lit along its top edge, where the content
        // meets it.
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.7f to colors.ink.copy(alpha = 0.62f),
                            1f to colors.ink.copy(alpha = 0.92f),
                        )
                    )
            )
            LiquidTabRow(
                current = current,
                savedCount = savedCount,
                bump = bump.value,
                onSelect = { tab ->
                    haptics?.play(if (tab == current) Haptic.Tap else Haptic.Select)
                    onSelect(tab)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .glassPane(PaneEdge.Top, colors.ink, opacity = 0.975f),
            )
        }
    }
}

/**
 * The tabs, with a liquid indicator.
 *
 * Seven tabs is two more than Material's bar is built for, so the row is laid
 * out by hand: equal columns, an icon over a one-line label. Behind the icon
 * sits the indicator, a soft pill with two edges on two springs. Moving to a
 * tab on the right, the right edge leaps ahead and the left edge follows a
 * beat later, so the pill stretches toward where you tapped, thins a little
 * as it stretches, and gathers itself up when it arrives - a drop of liquid
 * rather than a box that teleports.
 */
@Composable
private fun LiquidTabRow(
    current: Tab,
    savedCount: Int,
    bump: Float,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CvTheme.colors
    val reduced = CvTheme.reducedMotion
    val tabs = Tab.entries
    val index = tabs.indexOf(current).toFloat()
    val lead = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(index) }
    val trail = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(index) }
    androidx.compose.runtime.LaunchedEffect(index) {
        if (reduced) {
            lead.snapTo(index); trail.snapTo(index)
            return@LaunchedEffect
        }
        // The front edge is quick and springy, the back edge slower and
        // calmer; which edge is "front" depends on the direction of travel.
        kotlinx.coroutines.coroutineScope {
            launch { lead.animateTo(index, androidx.compose.animation.core.spring(dampingRatio = 0.62f, stiffness = 900f)) }
            launch { trail.animateTo(index, androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 220f)) }
        }
    }
    val pill = colors.text.copy(alpha = 0.09f)
    val sheen = colors.text.copy(alpha = 0.05f)
    Row(
        modifier
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars)
            .height(64.dp)
            .drawBehind {
                val column = size.width / tabs.size
                val half = 27.dp.toPx()
                val left = minOf(lead.value, trail.value)
                val right = maxOf(lead.value, trail.value)
                val stretch = (right - left).coerceIn(0f, 2f)
                // Thinner as it stretches, like a drop pulled long.
                val height = 32.dp.toPx() * (1f - 0.16f * (stretch / 2f))
                val top = 7.dp.toPx() + (32.dp.toPx() - height) / 2f
                // The quick edge overshoots; it squashes against the bar's ends
                // rather than running off the screen.
                val inset = 3.dp.toPx()
                val x0 = (column * (left + 0.5f) - half).coerceAtLeast(inset)
                val x1 = (column * (right + 0.5f) + half).coerceAtMost(size.width - inset)
                val radius = androidx.compose.ui.geometry.CornerRadius(height / 2f)
                drawRoundRect(pill, Offset(x0, top), androidx.compose.ui.geometry.Size(x1 - x0, height), radius)
                // A lit upper half, so it reads as glass rather than a flat patch.
                drawRoundRect(
                    Brush.verticalGradient(listOf(sheen, Color.Transparent), startY = top, endY = top + height),
                    Offset(x0, top), androidx.compose.ui.geometry.Size(x1 - x0, height), radius,
                )
            },
    ) {
        for (tab in tabs) {
            val selected = tab == current
            val scale by animateFloatAsState(
                targetValue = if (selected) 1f else 0.94f,
                animationSpec = Motion.lively(),
                label = "tab",
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        role = androidx.compose.ui.semantics.Role.Tab,
                    ) { onSelect(tab) }
                    .semantics { this.selected = selected },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(7.dp))
                Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
                    // Drawn, not glyphed: it fills from the foot up as it
                    // becomes the tab you are on.
                    TabIcon(
                        glyph = tab.glyph,
                        selected = selected,
                        color = if (selected) colors.text else colors.text3,
                        modifier = Modifier.graphicsLayer {
                            val pop = if (tab == Tab.MyList) bump * 0.28f else 0f
                            if (!reduced) { scaleX = scale + pop; scaleY = scale + pop }
                        },
                    )
                    // The count of titles waiting for you, the one number worth
                    // putting on a tab.
                    if (tab == Tab.MyList && savedCount > 0) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 3.dp)
                                .offset(x = 4.dp)
                                .size(7.dp)
                                .background(Palette.Red2, androidx.compose.foundation.shape.CircleShape)
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, letterSpacing = 0.sp),
                    color = if (selected) colors.text else colors.text3,
                    maxLines = 1,
                    softWrap = false,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                )
            }
        }
    }
}
