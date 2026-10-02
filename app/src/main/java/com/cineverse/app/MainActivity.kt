package com.cineverse.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cineverse.app.nav.DeepLinks
import com.cineverse.app.nav.Route
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cineverse.app.core.design.CineVerseTheme
import com.cineverse.app.core.design.CvHaptics
import com.cineverse.app.nav.CineVerseNav

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Held until the settings have been read, so the app never paints dark
        // for a frame and then flips to paper for someone who chose light.
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = (application as CineVerseApp).container
        var ready = false
        splash.setKeepOnScreenCondition { !ready }

        // One Vibrator for the whole app, reading the preference each time
        // rather than capturing it, so turning haptics off takes effect at once.
        val haptics = CvHaptics(this) { app.settings.settings.value.haptics }

        // Anything the website changed since this device last ran.
        app.settings.syncFromCloud()

        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle()
            // A shortcut, a widget row, a shared link or a cineverse:// URL.
            // Held in state so `onNewIntent` can route a second one without the
            // activity being recreated.
            var pending by androidx.compose.runtime.remember {
                mutableStateOf(DeepLinks.routeFor(intent))
            }
            androidx.compose.runtime.DisposableEffect(Unit) {
                val listener = androidx.core.util.Consumer<Intent> { next ->
                    DeepLinks.routeFor(next)?.let { pending = it }
                }
                addOnNewIntentListener(listener)
                onDispose { removeOnNewIntentListener(listener) }
            }
            ready = true
            CineVerseTheme(
                theme = settings.theme,
                dynamicColor = settings.dynamicColor,
                motion = settings.motion,
                haptics = haptics,
            ) {
                CineVerseNav(app, pending) { pending = null }
            }
        }
    }
}
