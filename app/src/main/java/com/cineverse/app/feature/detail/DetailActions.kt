package com.cineverse.app.feature.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.cineverse.app.data.model.TitleDetail

/**
 * A trailer opens in a Custom Tab rather than an in-app WebView.
 *
 * It is the honest choice: YouTube's own player, with the user's own sign-in,
 * their playback quality, their captions and their history — and it comes back
 * to CineVerse when they are done. An embedded IFrame would be a worse player
 * wearing our chrome, and the terms of use for embedding are stricter than for
 * linking.
 */
fun openTrailer(context: Context, key: String) {
    val watch = Uri.parse("https://www.youtube.com/watch?v=$key")
    // The YouTube app first, where it exists: it is a better player than any
    // browser and it is almost always installed.
    val app = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$key")).apply {
        putExtra("force_fullscreen", true)
    }
    if (app.resolveActivity(context.packageManager) != null) {
        runCatching { context.startActivity(app) }.onSuccess { return }
    }
    runCatching {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, watch)
    }.onFailure {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, watch)) }
    }
}

/** Share a title as a link to the website, so the recipient lands somewhere real. */
fun shareTitle(context: Context, detail: TitleDetail) {
    val url = "https://cineverse.pages.dev/${detail.type.wire}/${detail.id}"
    val text = buildString {
        append(detail.title)
        if (detail.year.isNotBlank()) append(" (${detail.year})")
        append("\n")
        append(url)
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_TITLE, detail.title)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Share ${detail.title}"))
    }
}
