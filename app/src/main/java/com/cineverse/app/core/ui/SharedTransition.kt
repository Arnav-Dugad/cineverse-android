package com.cineverse.app.core.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Motion

/**
 * The poster that travels.
 *
 * Tapping a poster and having the title page simply appear is the moment an app
 * stops feeling like a set of screens. Here the poster you touched IS the poster
 * on the page you land on: one object, moving and growing, so there is never a
 * question of what you opened or how to get back to it.
 *
 * Two scopes have to reach the poster for that to work and neither can be passed
 * down by hand through a dozen rails, so both are composition locals. They are
 * `compositionLocalOf` with a null default rather than `staticCompositionLocalOf`
 * because the value genuinely changes per destination, and anything drawn
 * outside a NavHost — a sheet, a widget preview, a test — must render without
 * them rather than crash.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope: ProvidableCompositionLocal<SharedTransitionScope?> =
    compositionLocalOf { null }

val LocalNavAnimatedScope: ProvidableCompositionLocal<AnimatedVisibilityScope?> =
    compositionLocalOf { null }

/**
 * The spring the shared bounds travel on.
 *
 * Deliberately the landing spring and not the lively one: a poster that
 * overshoots its own page and settles back looks like a mistake at this size,
 * where the same overshoot on a 46dp button reads as life.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
private val PosterBounds = BoundsTransform { _, _ -> Motion.landing() }

/**
 * Marks a poster as the same poster in two places.
 *
 * The key has to agree on both ends and nowhere else, which is why it is built
 * here from the item key rather than spelled out at each call site: two rails
 * showing the same title at once would otherwise both claim the match and the
 * transition would pick one at random.
 *
 * Does nothing at all when either scope is missing, or when the user has asked
 * for reduced motion — in which case the destination simply cross-fades, which
 * is the correct behaviour and not a degraded one.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedPoster(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    if (CvTheme.reducedMotion) return this
    return with(shared) {
        this@sharedPoster.sharedElement(
            sharedContentState = rememberSharedContentState(key = "poster/$key"),
            animatedVisibilityScope = animated,
            boundsTransform = PosterBounds,
        )
    }
}
