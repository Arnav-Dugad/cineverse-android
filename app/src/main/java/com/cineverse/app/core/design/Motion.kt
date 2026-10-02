package com.cineverse.app.core.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Motion, in one place.
 *
 * The rule the app is held to: nothing moves without telling you something.
 * An animation here either shows where a thing came from, how far through you
 * are, or that what you did worked.
 *
 * Interactive motion is a SPRING, never a duration, so a gesture interrupted
 * halfway is picked up from where it actually is rather than snapping to where
 * a timeline thought it would be. Durations are kept for things that are not
 * being dragged: a cross-fade, a shimmer, a count-up.
 */
object Motion {

    /** The website's --ease-out: a long, late settle. Decelerating, never bouncy. */
    val EaseOut: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** The website's --ease: Material's standard curve. */
    val Standard: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** The website's --bounce, for a thing that lands. */
    val Bounce: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

    // ---------- springs ----------

    /** A press, a tick, a chip: fast and completely settled, no visible wobble. */
    fun <T> snappy(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** A card lifting, a sheet arriving: one gentle overshoot. */
    fun <T> lively(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.68f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Something that lands with weight — the ticket stub, a badge unlocking. */
    fun <T> landing(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.52f,
        stiffness = Spring.StiffnessLow,
    )

    /** A large surface moving a long way: slow enough to be followed. */
    fun <T> gentle(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessVeryLow,
    )

    /** Position springs need their own visibility threshold to settle cleanly. */
    fun offset(): FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = 0.75f,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntOffset(1, 1),
    )

    fun size(): FiniteAnimationSpec<IntSize> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntSize(1, 1),
    )

    fun dp(): FiniteAnimationSpec<Dp> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = Dp.VisibilityThreshold,
    )

    // ---------- durations ----------

    const val Instant = 90
    const val Quick = 180
    const val Normal = 280
    const val Slow = 420
    const val Cinematic = 900

    fun <T> fade(durationMillis: Int = Normal): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = EaseOut)

    /** The stagger between neighbouring cards on a rail's first paint. */
    const val RailStaggerMs = 40L

    /** How long the hero holds a slide before it dissolves to the next. */
    const val HeroHoldMs = 7_000L
    const val HeroFadeMs = 1_200
}
