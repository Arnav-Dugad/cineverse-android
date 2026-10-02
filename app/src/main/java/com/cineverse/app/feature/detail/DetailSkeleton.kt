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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
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
    Column(
        modifier.fillMaxSize().background(colors.ink),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The hero, with the same mask the real one carries, so the join
        // between artwork and page is already in the right place.
        Box(Modifier.fillMaxWidth().height(372.dp)) {
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

        // EXACTLY the real head's geometry, down to the overlap.
        //
        // This used to be a 112x168 poster on the left, which was right for the
        // head the page used to have. After the head was centred and enlarged
        // the shared poster flew from the rail into the skeleton's slot and
        // then JUMPED to the real one, because the shared element retargets
        // when the destination changes mid-flight. A skeleton whose job is to
        // reserve the right room has to reserve the right room.
        Box(
            Modifier.height(POSTER_HEIGHT - POSTER_OVERLAP),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                Modifier
                    .offset(y = -POSTER_OVERLAP)
                    .requiredSize(width = POSTER_WIDTH, height = POSTER_HEIGHT)
                    .sharedPoster(itemKey)
                    .clip(CvShape.Large)
                    .shimmer()
            )
        }

        Spacer(Modifier.height(12.dp))
        Bar(220.dp, 44.dp, CvShape.Small)
        Spacer(Modifier.height(12.dp))
        Bar(170.dp, 14.dp)

        Spacer(Modifier.height(16.dp))
        // The chip flow.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Bar(66.dp, 32.dp, CvShape.Pill)
            Bar(84.dp, 32.dp, CvShape.Pill)
            Bar(62.dp, 32.dp, CvShape.Pill)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Bar(78.dp, 32.dp, CvShape.Pill)
            Bar(96.dp, 32.dp, CvShape.Pill)
        }

        Spacer(Modifier.height(20.dp))
        // One wide button and four circles, on one line, as the real row is.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).height(48.dp).clip(CvShape.Pill).shimmer())
            repeat(4) { Box(Modifier.size(48.dp).clip(CircleShape).shimmer()) }
        }

        Spacer(Modifier.height(20.dp))
        Column(Modifier.padding(horizontal = ScreenPadding)) {
            Bar(Dp.Unspecified, 14.dp)
            Spacer(Modifier.height(7.dp))
            Bar(Dp.Unspecified, 14.dp)
            Spacer(Modifier.height(7.dp))
            Bar(220.dp, 14.dp)
        }
    }
}

private val POSTER_WIDTH = 172.dp
private val POSTER_HEIGHT = 258.dp
private val POSTER_OVERLAP = 104.dp

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
