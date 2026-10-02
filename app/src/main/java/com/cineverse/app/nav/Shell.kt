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

/**
 * The bottom bar hides on the way down and comes back on the way up.
 *
 * Browsing a rail of posters should get the whole screen; the bar is never more
 * than a flick away. The threshold is deliberately asymmetric — 24dp of
 * downward scroll to hide, 4dp of upward to show — because reaching for the bar
 * is an intentional act and losing it is not.
 */
class BarVisibility {
    var visible by mutableStateOf(true)
        private set

    private var travelled by mutableFloatStateOf(0f)

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy < 0) {
                travelled = (travelled + dy).coerceAtLeast(-120f)
                if (travelled < -24f) visible = false
            } else if (dy > 0) {
                travelled = (travelled + dy).coerceAtMost(120f)
                if (travelled > 4f) visible = true
            }
            return Offset.Zero
        }
    }

    fun show() { visible = true; travelled = 0f }
}

@Composable
fun CvNavigationBar(
    current: Tab,
    bars: BarVisibility,
    onSelect: (Tab) -> Unit,
    savedCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    AnimatedVisibility(
        visible = bars.visible || CvTheme.reducedMotion,
        enter = slideInVertically(Motion.lively()) { it },
        exit = slideOutVertically(Motion.snappy()) { it },
        modifier = modifier,
    ) {
        // The fade goes ABOVE the bar, not behind it.
        //
        // Two attempts got this wrong. A fixed-height Box left the bar floating
        // off the screen's edge; a gradient as the bar's own background left
        // poster captions legible straight through the labels, because the top
        // of that gradient is where the icons are. A row of text behind a row of
        // labels is the single ugliest thing a bottom bar can do, so the ramp
        // happens in its own strip and the bar itself sits on solid page colour.
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, colors.ink.copy(alpha = 0.92f), colors.ink)
                        )
                    )
            )
            NavigationBar(
                containerColor = colors.ink,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth(),
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
                                targetValue = if (selected) 1f else 0.92f,
                                animationSpec = Motion.lively(),
                                label = "tab",
                            )
                            // Read outside the graphics layer: the layer block runs
                            // on the draw pass, where composition locals are not.
                            val reduced = CvTheme.reducedMotion
                            Box {
                                Icon(
                                    if (selected) tab.selectedIcon else tab.icon,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .graphicsLayer {
                                            if (!reduced) { scaleX = scale; scaleY = scale }
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
                            indicatorColor = colors.glassStrong,
                        ),
                    )
                }
            }
        }
    }
}
