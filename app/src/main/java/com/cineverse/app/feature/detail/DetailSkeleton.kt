package com.cineverse.app.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.sharedPoster
import com.cineverse.app.core.ui.shimmer

/**
 * A title page before its data arrives.
 *
 * The thing being avoided here is a full-bleed grey sweep, which is what this
 * screen used to show. A skeleton that is not the SHAPE of the page it stands in
 * for does two bad things at once: it tells you nothing about what is coming,
 * and when the real content lands every element on screen jumps, because the
 * placeholder never reserved the right room for anything. This one has the hero
 * at its real 420dp, the poster at its real 104x156, the action row at its real
 * 46dp, and the first episode rows at theirs — so the arrival is a crossfade and
 * not a reflow.
 *
 * It is also deliberately INCOMPLETE below the fold. Drawing twelve fake rows
 * down a page that might only have three is its own kind of lie.
 */
@Composable
fun DetailSkeleton(itemKey: String, modifier: Modifier = Modifier) {
    val colors = CvTheme.colors
    Column(modifier.fillMaxSize().background(colors.ink)) {
        // The hero, with the same mask the real one carries, so the join between
        // artwork and page is already in the right place.
        Box(Modifier.fillMaxWidth().height(420.dp)) {
            Box(Modifier.fillMaxSize().shimmer())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to colors.ink.copy(alpha = 0.55f),
                            0.45f to Color.Transparent,
                            0.78f to colors.ink.copy(alpha = 0.55f),
                            1f to colors.ink,
                        )
                    )
            )
        }

        Column(Modifier.padding(horizontal = ScreenPadding)) {
            Row(verticalAlignment = Alignment.Top) {
                // The poster claims the shared key even while it is still a
                // placeholder, so a title opened cold still has its poster fly
                // in from the rail rather than appearing out of a grey rectangle.
                Box(
                    Modifier
                        .width(112.dp)
                        .height(168.dp)
                        .sharedPoster(itemKey)
                        .clip(CvShape.Large)
                        .shimmer()
                )
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Bar(180.dp, 26.dp)
                    Spacer(Modifier.height(8.dp))
                    Bar(120.dp, 14.dp)
                    Spacer(Modifier.height(8.dp))
                    Bar(150.dp, 12.dp)
                }
            }

            Spacer(Modifier.height(14.dp))
            // The score pills.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Bar(62.dp, 30.dp, CvShape.Pill)
                Bar(74.dp, 30.dp, CvShape.Pill)
                Bar(74.dp, 30.dp, CvShape.Pill)
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Bar(72.dp, 26.dp, CvShape.Pill)
                Bar(58.dp, 26.dp, CvShape.Pill)
            }

            Spacer(Modifier.height(18.dp))
            // The action row: one wide button and four circles, exactly as it is.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(CvShape.Pill)
                        .shimmer()
                )
                repeat(4) {
                    Box(Modifier.size(46.dp).clip(CircleShape).shimmer())
                }
            }

            Spacer(Modifier.height(18.dp))
            Bar(Dp.Unspecified, 14.dp)
            Spacer(Modifier.height(7.dp))
            Bar(Dp.Unspecified, 14.dp)
            Spacer(Modifier.height(7.dp))
            Bar(220.dp, 14.dp)

            Spacer(Modifier.height(22.dp))
            // The segmented control.
            Box(Modifier.fillMaxWidth().height(44.dp).clip(CvShape.Pill).shimmer())

            Spacer(Modifier.height(18.dp))
            // Two episode rows, which is enough to say what kind of page this is.
            repeat(2) {
                Row(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                    Bar(130.dp, 74.dp, CvShape.Medium)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Bar(Dp.Unspecified, 15.dp)
                        Spacer(Modifier.height(7.dp))
                        Bar(160.dp, 12.dp)
                        Spacer(Modifier.height(7.dp))
                        Bar(90.dp, 12.dp)
                    }
                }
            }
        }
    }
}

/** One placeholder. An unspecified width fills the row. */
@Composable
private fun Bar(
    width: Dp,
    height: Dp,
    shape: androidx.compose.ui.graphics.Shape = CvShape.Tiny,
) {
    Box(
        (if (width == Dp.Unspecified) Modifier.fillMaxWidth() else Modifier.width(width))
            .height(height)
            .clip(shape)
            .shimmer()
    )
}
