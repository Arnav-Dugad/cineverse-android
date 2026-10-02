package com.cineverse.app.core.design

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Which palette the app paints in. Mirrors the website's three-way setting. */
enum class ThemeChoice { System, Dark, Light }

/**
 * How much the app moves. `System` follows Android's "Remove animations"
 * accessibility setting; the other two override it in both directions, because
 * a viewer who turned animations off system-wide may still want them here, and
 * someone who did not may still find a hero that never stops distracting.
 */
enum class MotionChoice { System, Full, Reduced }

private val LocalCvColors = staticCompositionLocalOf { DarkCvColors }
private val LocalReducedMotion = staticCompositionLocalOf { false }

object CvTheme {
    val colors: CvColors
        @Composable @ReadOnlyComposable get() = LocalCvColors.current

    /** True when the app should cross-fade instead of animate. */
    val reducedMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReducedMotion.current
}

@Composable
fun CineVerseTheme(
    theme: ThemeChoice = ThemeChoice.System,
    /** Take the accent from the wallpaper instead of CineVerse red. */
    dynamicColor: Boolean = false,
    motion: MotionChoice = MotionChoice.System,
    haptics: CvHaptics? = null,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (theme) {
        ThemeChoice.System -> systemDark
        ThemeChoice.Dark -> true
        ThemeChoice.Light -> false
    }
    val context = LocalContext.current

    // Material You, when asked for. The extended CineVerse tokens are NOT
    // derived from the wallpaper: a rating is gold and a Tomatometer is red
    // whatever the phone's accent is, because those are other people's brands
    // and a heatmap band has to mean the same score on every device.
    val scheme: ColorScheme = when {
        dynamicColor && dark -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        dark -> CineVerseDarkScheme
        else -> CineVerseLightScheme
    }
    val cvColors = if (dark) DarkCvColors else LightCvColors

    val reduced = when (motion) {
        MotionChoice.Full -> false
        MotionChoice.Reduced -> true
        MotionChoice.System -> rememberSystemAnimationsDisabled()
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // Edge to edge: the app paints behind the status and navigation bars
            // and insets its own content, so artwork reaches the physical edge.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalCvColors provides cvColors,
        LocalReducedMotion provides reduced,
        LocalHaptics provides haptics,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = CineVerseTypography,
            shapes = CineVerseShapes,
            content = content,
        )
    }
}

/**
 * Android's "Remove animations" accessibility setting, read once per composition.
 * `Settings.Global.ANIMATOR_DURATION_SCALE` is 0 when the user has turned
 * animations off; anything else is a scale factor.
 */
@Composable
private fun rememberSystemAnimationsDisabled(): Boolean {
    val context = LocalContext.current
    return runCatching {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }.getOrDefault(false)
}
