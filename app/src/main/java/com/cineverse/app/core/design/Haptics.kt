package com.cineverse.app.core.design

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Feel.
 *
 * The website has a haptic vocabulary (js/haptics.js) built on `navigator.vibrate`,
 * which can only ask for "buzz for N milliseconds". A phone's actuator can do far
 * better than that, and this is the one part of the app that is strictly better
 * than the site rather than a port of it: every signature below is composed from
 * the hardware's own PRIMITIVES — a tick, a click, a thud, a rise — so a tick
 * feels like a tick instead of a short buzz.
 *
 * The vocabulary is the same, so the two products agree about what things feel
 * like:
 *
 *   Detent   a row passing under the thumb, a value stepping
 *   Land     something arriving at rest
 *   Edge     the end of a rail; you cannot go further
 *   Tap      a plain press
 *   Tick     an episode marked watched
 *   Untick   the same, undone — deliberately lighter, so they are distinguishable
 *   Select   a choice committed
 *   Peek     a long press has caught; something is about to open
 *   Drop     a dragged thing let go
 *   Success  a sweep finished, a list saved
 *   Warning  refused, or about to be destructive
 *   Celebrate a milestone, a badge, a finished series
 *
 * Nothing fires without the user's preference being on, and nothing fires on a
 * device without an amplitude-controlled actuator — a flat buzz is worse than
 * silence.
 */
enum class Haptic {
    Detent, Land, Edge, Tap, Tick, Untick, Select, Peek, Drop, Success, Warning, Celebrate,
}

@Immutable
class CvHaptics(context: Context, private val enabled: () -> Boolean) {

    private val vibrator: Vibrator? = runCatching {
        val manager = context.getSystemService(VibratorManager::class.java)
        manager?.defaultVibrator
    }.getOrNull()

    private val canCompose: Boolean =
        vibrator?.areAllPrimitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_CLICK,
            VibrationEffect.Composition.PRIMITIVE_TICK,
        ) == true

    private val hasAmplitude: Boolean = vibrator?.hasAmplitudeControl() == true

    fun play(kind: Haptic) {
        val device = vibrator ?: return
        if (!enabled() || !device.hasVibrator()) return
        val effect = if (canCompose) composed(kind) else fallback(kind)
        runCatching { device.vibrate(effect) }
    }

    /**
     * The good path: primitives, each with its own strength and a gap before it,
     * which is what gives a double-tap signature its rhythm.
     */
    private fun composed(kind: Haptic): VibrationEffect {
        val c = VibrationEffect.startComposition()
        val tick = VibrationEffect.Composition.PRIMITIVE_TICK
        val click = VibrationEffect.Composition.PRIMITIVE_CLICK
        val lowTick = if (supports(VibrationEffect.Composition.PRIMITIVE_LOW_TICK))
            VibrationEffect.Composition.PRIMITIVE_LOW_TICK else tick
        val thud = if (supports(VibrationEffect.Composition.PRIMITIVE_THUD))
            VibrationEffect.Composition.PRIMITIVE_THUD else click
        val rise = if (supports(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE))
            VibrationEffect.Composition.PRIMITIVE_QUICK_RISE else click
        val spin = if (supports(VibrationEffect.Composition.PRIMITIVE_SPIN))
            VibrationEffect.Composition.PRIMITIVE_SPIN else click

        when (kind) {
            // Barely there, and the one that fires most often: a row stepping past.
            Haptic.Detent -> c.addPrimitive(lowTick, 0.22f)
            Haptic.Land -> c.addPrimitive(tick, 0.4f)
            // A wall. Low and dull, so it reads as "stop", not "done".
            Haptic.Edge -> c.addPrimitive(thud, 0.5f)
            Haptic.Tap -> c.addPrimitive(tick, 0.55f)
            // Two beats rising: the shape of a thing being confirmed.
            Haptic.Tick -> c.addPrimitive(tick, 0.5f).addPrimitive(click, 0.85f, 36)
            // The same gesture undone, falling instead of rising, and lighter.
            Haptic.Untick -> c.addPrimitive(click, 0.45f).addPrimitive(lowTick, 0.3f, 32)
            Haptic.Select -> c.addPrimitive(click, 0.7f)
            // A long press catching: a rise, so it feels like something lifting.
            Haptic.Peek -> c.addPrimitive(rise, 0.5f)
            Haptic.Drop -> c.addPrimitive(thud, 0.8f)
            Haptic.Success -> c.addPrimitive(tick, 0.5f).addPrimitive(click, 1f, 48)
            // Deliberately uncomfortable: two heavy thuds close together.
            Haptic.Warning -> c.addPrimitive(thud, 0.9f).addPrimitive(thud, 0.9f, 90)
            Haptic.Celebrate -> c
                .addPrimitive(tick, 0.4f)
                .addPrimitive(click, 0.7f, 60)
                .addPrimitive(spin, 0.8f, 60)
                .addPrimitive(click, 1f, 70)
        }
        return c.compose()
    }

    private fun supports(primitive: Int): Boolean =
        vibrator?.arePrimitivesSupported(primitive)?.firstOrNull() == true

    /**
     * No primitives: fall back to timings and amplitudes, which is still better
     * than the website's flat `vibrate(ms)` wherever amplitude control exists.
     */
    private fun fallback(kind: Haptic): VibrationEffect {
        val (timings, amplitudes) = when (kind) {
            Haptic.Detent -> longArrayOf(0, 6) to intArrayOf(0, 40)
            Haptic.Land -> longArrayOf(0, 8) to intArrayOf(0, 70)
            Haptic.Edge -> longArrayOf(0, 14) to intArrayOf(0, 120)
            Haptic.Tap -> longArrayOf(0, 9) to intArrayOf(0, 90)
            Haptic.Tick -> longArrayOf(0, 8, 30, 14) to intArrayOf(0, 80, 0, 160)
            Haptic.Untick -> longArrayOf(0, 10, 26, 6) to intArrayOf(0, 110, 0, 55)
            Haptic.Select -> longArrayOf(0, 11) to intArrayOf(0, 130)
            Haptic.Peek -> longArrayOf(0, 7, 24, 11) to intArrayOf(0, 55, 0, 110)
            Haptic.Drop -> longArrayOf(0, 16) to intArrayOf(0, 180)
            Haptic.Success -> longArrayOf(0, 9, 36, 16) to intArrayOf(0, 90, 0, 200)
            Haptic.Warning -> longArrayOf(0, 18, 40, 20) to intArrayOf(0, 200, 0, 210)
            Haptic.Celebrate -> longArrayOf(0, 10, 40, 12, 40, 24) to
                intArrayOf(0, 90, 0, 140, 0, 220)
        }
        return if (hasAmplitude) {
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }
    }
}

/** A no-op stand-in, so a preview or a test never has to own a Vibrator. */
val LocalHaptics = staticCompositionLocalOf<CvHaptics?> { null }
