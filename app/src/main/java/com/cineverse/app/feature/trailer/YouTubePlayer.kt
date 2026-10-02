package com.cineverse.app.feature.trailer

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView

/**
 * A trailer, played by YouTube, inside CineVerse.
 *
 * The honest way to do this. CineVerse does not host a single frame of video,
 * and pulling a stream out of YouTube to feed an ExoPlayer would be both a
 * licence breach and a thing that breaks every few months. The IFrame player is
 * the method YouTube publishes FOR this: it is their player, their ads, their
 * analytics, their quality ladder, and it is exactly what the website embeds, so
 * the two platforms behave the same.
 *
 * What the app provides around it is what an app can provide — fullscreen,
 * picture-in-picture, the back gesture, and a surface that matches the page it
 * came from rather than a browser chrome dropped on top of it.
 *
 * The notable details:
 *
 *  - `playsinline=1`, or Android hands the video to the system player and the
 *    whole point is lost.
 *  - `origin` must be set or the JS API refuses to initialise on some builds.
 *  - Navigation is PINNED to the embed. A trailer page with a "Watch on
 *    YouTube" link in it must not be able to turn this WebView into a browser;
 *    anything that is not the player opens in the real YouTube app instead.
 *  - The WebView is destroyed on dispose. One leaked WebView holds an entire
 *    renderer process, and on a page people open and close all evening that
 *    adds up fast.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayer(
    videoKey: String,
    modifier: Modifier = Modifier,
    autoplay: Boolean = true,
    muted: Boolean = false,
    showControls: Boolean = true,
    loop: Boolean = false,
    /**
     * Fill the box and crop, with the chrome pushed outside it, and ignore every
     * touch. This is the hero behind a page rather than a video someone chose to
     * watch, and it has to behave like wallpaper.
     */
    ambient: Boolean = false,
    onReady: () -> Unit = {},
    onPlaying: () -> Unit = {},
    onEnded: () -> Unit = {},
) {
    val html = remember(videoKey, autoplay, muted, showControls, loop, ambient) {
        embedHtml(videoKey, autoplay, muted, showControls, loop, ambient)
    }
    var holder: WebView? = remember { null }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                holder = this
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(Color.Black.toArgb())
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                if (ambient) {
                    // A DESKTOP user agent, and this is the whole fix.
                    //
                    // Android WebView gets YouTube's mobile embed, and the
                    // mobile embed draws its centre play/pause/skip overlay
                    // whatever `controls=0` says -- it sat over the hero through
                    // two attempts at CSS. The desktop embed honours the flag.
                    // The player is identical either way; only the chrome
                    // differs, and this one is asking for no chrome at all.
                    settings.userAgentString = DESKTOP_AGENT
                    // Every touch eaten. One stray tap on an ambient trailer
                    // brings up YouTube's whole control overlay on top of the
                    // hero, and there is no way back from it -- the user did not
                    // ask for a player, so they must not be able to summon one.
                    setOnTouchListener { _, _ -> true }
                    isFocusable = false
                    isFocusableInTouchMode = false
                }
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                }
                addJavascriptInterface(
                    object {
                        @android.webkit.JavascriptInterface
                        fun onState(state: Int) {
                            // YouTube's own codes: 1 playing, 0 ended.
                            post {
                                when (state) {
                                    1 -> onPlaying()
                                    0 -> onEnded()
                                    5 -> onReady()
                                }
                            }
                        }
                    },
                    "CineVerse",
                )
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val url = request.url.toString()
                        // The player itself may navigate within the embed; a tap
                        // on anything else leaves for the real YouTube.
                        if (url.contains("/embed/")) return false
                        runCatching {
                            view.context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, request.url)
                            )
                        }
                        return true
                    }
                }
                loadDataWithBaseURL(ORIGIN, html, "text/html", "utf-8", null)
            }
        },
    )

    DisposableEffect(Unit) {
        onDispose {
            holder?.apply {
                // Stop the sound BEFORE tearing the view down: destroy() alone
                // can leave a frame of audio playing into the next screen.
                loadUrl("about:blank")
                stopLoading()
                removeAllViews()
                destroy()
            }
            holder = null
        }
    }
}

private const val ORIGIN = "https://cineverse.pages.dev"

private const val DESKTOP_AGENT =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"

private fun embedHtml(
    key: String,
    autoplay: Boolean,
    muted: Boolean,
    controls: Boolean,
    loop: Boolean,
    ambient: Boolean,
): String {
    val params = buildString {
        append("autoplay=").append(if (autoplay) 1 else 0)
        append("&mute=").append(if (muted) 1 else 0)
        append("&controls=").append(if (controls) 1 else 0)
        append("&playsinline=1&rel=0&iv_load_policy=3&cc_load_policy=0")
        append("&modestbranding=1&enablejsapi=1&disablekb=1&fs=0")
        if (loop) append("&loop=1&playlist=").append(key)
        append("&origin=").append(ORIGIN)
    }

    // Two completely different jobs, so two completely different boxes.
    //
    // A chosen trailer is CONTAINED: the whole frame, letterboxed if it has to
    // be, because cropping a shot someone sat down to watch is vandalism.
    //
    // An ambient trailer COVERS, the way a backdrop does -- and it is scaled a
    // further 1.25 on top of that, which is the trick the website's video
    // background uses: YouTube draws its title, its watermark and its control
    // overlay INSIDE the iframe, so the only reliable way to be rid of them is
    // to push them past the edges of a box that clips. `controls=0` alone does
    // not do it; the first screenshot of this had a pause button over the hero.
    val css = if (ambient) """
        html, body { margin: 0; padding: 0; height: 100%; background: #000; overflow: hidden; }
        #wrap { position: absolute; inset: 0; overflow: hidden; }
        iframe {
          position: absolute; top: 50%; left: 50%;
          width: 100vw; height: 56.25vw;
          min-height: 100vh; min-width: 177.78vh;
          transform: translate(-50%, -50%) scale(1.25);
          border: 0; pointer-events: none;
        }
    """ else """
        html, body { margin: 0; padding: 0; height: 100%; background: #000; overflow: hidden; }
        #wrap { position: absolute; inset: 0; }
        iframe { position: absolute; inset: 0; width: 100%; height: 100%; border: 0; }
    """

    return """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
          <style>$css</style>
        </head>
        <body>
          <div id="wrap">
            <iframe id="p" src="https://www.youtube.com/embed/$key?$params"
                    allow="autoplay; encrypted-media; picture-in-picture"
                    allowfullscreen></iframe>
          </div>
          <script src="https://www.youtube.com/iframe_api"></script>
          <script>
            function onYouTubeIframeAPIReady() {
              new YT.Player('p', {
                events: {
                  onReady: function (e) {
                    try { CineVerse.onState(5); } catch (err) {}
                  },
                  onStateChange: function (e) {
                    try { CineVerse.onState(e.data); } catch (err) {}
                  }
                }
              });
            }
          </script>
        </body>
        </html>
    """.trimIndent()
}
