package com.cineverse.app.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
            NavigationBar(
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .glassPane(PaneEdge.Top, colors.ink, opacity = 0.975f),
            ) {
                for (tab in Tab.entries) {
                    val selected = tab == current
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            haptics?.play(if (selected) Haptic.Tap else Haptic.Select)
                            onSelect(tab)
                        },
                        icon = {
                            val scale by animateFloatAsState(
                                targetValue = if (selected) 1f else 0.94f,
                                animationSpec = Motion.lively(),
                                label = "tab",
                            )
                            // Read outside the graphics layer: the layer block runs
                            // on the draw pass, where composition locals are not.
                            val reduced = CvTheme.reducedMotion
                            Box {
                                // Drawn, not glyphed: it fills from the foot up
                                // as it becomes the tab you are on, which is the
                                // same transition the whole icon set uses.
                                TabIcon(
                                    glyph = tab.glyph,
                                    selected = selected,
                                    color = if (selected) colors.text else colors.text3,
                                    modifier = Modifier.graphicsLayer {
                                        val pop = if (tab == Tab.MyList) bump.value * 0.28f else 0f
                                        if (!reduced) { scaleX = scale + pop; scaleY = scale + pop }
                                    },
                                )
                                // The count of titles waiting for you, which is
                                // the one number worth putting on a tab.
                                if (tab == Tab.MyList && savedCount > 0) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(start = 10.dp)
                                            .size(7.dp)
                                            .background(Palette.Red2, androidx.compose.foundation.shape.CircleShape)
                                    )
                                }
                            }
                        },
                        label = {
                            Text(tab.label, style = MaterialTheme.typography.labelSmall)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = colors.text,
                            selectedTextColor = colors.text,
                            unselectedIconColor = colors.text3,
                            unselectedTextColor = colors.text3,
                            // Softer than the panels: an indicator as bright
                            // as a card competes with the icon inside it.
                            indicatorColor = colors.text.copy(alpha = 0.07f),
                        ),
                    )
                }
            }
        }
    }
}
