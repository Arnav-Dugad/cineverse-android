package com.cineverse.app.feature.trailer

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.util.Rational
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.feature.detail.openTrailer
import kotlinx.coroutines.delay

/**
 * A trailer, full screen, with the app still around it.
 *
 * Leaving CineVerse to watch a two-minute trailer and coming back to a cold
 * start is the kind of small rudeness that adds up. This keeps the trailer in
 * the app, lets it carry on in picture-in-picture while you read the episode
 * list underneath, and still offers the door to YouTube proper for anyone who
 * would rather have the comments and the queue.
 *
 * The chrome fades out after three seconds and comes back on a tap, which is
 * what every video player on the phone already does, so nobody has to learn it.
 */
@Composable
fun TrailerScreen(
    videoKey: String,
    title: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val haptics = LocalHaptics.current
    var chrome by remember { mutableStateOf(true) }
    var inPip by remember { mutableStateOf(false) }

    val canPip = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    // The activity tells us when the window shrank, so the chrome can get out of
    // the way. A back arrow drawn into a 200dp PiP window is just noise.
    DisposableEffect(activity) {
        val listener = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> {
            inPip = it.isInPictureInPictureMode
            if (inPip) chrome = false
        }
        (activity as? androidx.activity.ComponentActivity)
            ?.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            (activity as? androidx.activity.ComponentActivity)
                ?.removeOnPictureInPictureModeChangedListener(listener)
        }
    }

    // Restarted by `touches`, so holding a finger on the screen keeps the bar
    // up rather than having it vanish three seconds after the first contact.
    var touches by remember { mutableIntStateOf(0) }
    LaunchedEffect(chrome, touches) {
        if (!chrome) return@LaunchedEffect
        delay(3_500)
        chrome = false
    }

    // Landscape and immersive, for as long as this screen is up.
    //
    // A 16:9 trailer in a portrait window is a letterbox strip with two thirds
    // of the screen painted black either side of it; turning the phone is the
    // obvious thing to do and so the player should do it. Both the rotation and
    // the hidden bars are undone on the way out, in an onDispose rather than on
    // the back button, because the back GESTURE does not go through the button.
    DisposableEffect(activity) {
        val window = activity?.window
        val previousOrientation = activity?.requestedOrientation
        val controller = window?.let {
            androidx.core.view.WindowCompat.getInsetsController(it, it.decorView)
        }
        activity?.requestedOrientation =
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        controller?.apply {
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat
                .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        // The screen must not go out three minutes into a trailer.
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            previousOrientation?.let { activity.requestedOrientation = it }
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Watched, not consumed.
            //
            // The first version toggled the chrome on a click, which meant our
            // bar and YouTube's controls took turns: tap once for ours, tap
            // again for theirs. A touch is now observed in the INITIAL pass and
            // passed straight through, so one tap brings up both and they fade
            // together. The player is YouTube's; the way back is ours; neither
            // should have to be hunted for.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        chrome = true
                        touches++
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        YouTubePlayer(
            videoKey = videoKey,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )

        if (!inPip) {
            AnimatedVisibility(
                visible = chrome,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xCC000000), Color.Transparent)
                            )
                        )
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Round(Icons.AutoMirrored.Rounded.ArrowBack, "Back") {
                        haptics?.play(Haptic.Tap); onBack()
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (canPip) {
                        Round(Icons.Rounded.PictureInPictureAlt, "Picture in picture") {
                            haptics?.play(Haptic.Tap)
                            activity?.enterTrailerPip()
                        }
                    }
                    Round(Icons.Rounded.OpenInNew, "Open in YouTube") {
                        haptics?.play(Haptic.Tap)
                        openTrailer(context, videoKey)
                    }
                }
            }
        }
    }
}

@Composable
private fun Round(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color(0x55000000))
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(21.dp))
    }
}

/**
 * Shrink to a floating window.
 *
 * 16:9 because that is the trailer, and letting Android pick would letterbox a
 * window that is already the size of a postage stamp. Failure is swallowed:
 * some devices and some managed profiles refuse PiP, and the right response is
 * for the button to do nothing rather than for the app to stop.
 */
fun Activity.enterTrailerPip() {
    runCatching {
        enterPictureInPictureMode(
            PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .setAutoEnterEnabled(true)
                .build()
        )
    }
}
