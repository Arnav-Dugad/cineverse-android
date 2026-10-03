package com.cineverse.app.nav

import android.content.Intent
import android.net.Uri
import com.cineverse.app.data.model.MediaType

/**
 * Where an incoming intent should land.
 *
 * Four sources, one answer:
 *
 *  - a launcher shortcut (`cineverse://search`, `://list`, `://continue`)
 *  - the Today widget opening the stats page (`cineverse://stats`)
 *  - the widget opening a show (`cineverse://tv/95396`)
 *  - a link to the website (`https://cineverse.pages.dev/tv/95396`)
 *  - text shared from another app, which is treated as a search
 *
 * Kept pure and separate from the activity so each of those is one line to test
 * rather than an instrumentation run.
 */
object DeepLinks {

    fun routeFor(intent: Intent?): Route? {
        if (intent == null) return null
        if (intent.action == Intent.ACTION_SEND) return Route.Search
        val data = intent.data ?: return null
        return routeFor(data)
    }

    fun routeFor(uri: Uri): Route? {
        val scheme = uri.scheme.orEmpty()
        val host = uri.host.orEmpty()
        val segments = uri.pathSegments.orEmpty()

        if (scheme == "cineverse") {
            return when (host) {
                "search" -> Route.Search
                "list" -> Route.MyList
                "continue" -> Route.Home
                "stats" -> Route.Stats
                "franchises" -> Route.Franchises
                "box-office" -> Route.BoxOffice
                "year" -> Route.YourYear(segments.firstOrNull()?.toIntOrNull() ?: 0)
                "collection" -> segments.firstOrNull()?.toIntOrNull()?.let { Route.Collection(it) }
                "movie", "tv" -> segments.firstOrNull()?.toIntOrNull()
                    ?.let { Route.Detail(it, host) }
                else -> null
            }
        }

        // https://cineverse.pages.dev/tv/95396, and /movie/27205
        val kind = segments.getOrNull(0).orEmpty()
        val id = segments.getOrNull(1)?.toIntOrNull()
        return when {
            id != null && (kind == "movie" || kind == "tv") ->
                Route.Detail(id, MediaType.of(kind).wire)
            kind == "person" && id != null -> Route.Person(id)
            kind == "search" -> Route.Search
            kind == "watchlist" || kind == "list" -> Route.MyList
            kind == "stats" -> Route.Stats
            // The website's own paths for the pages the app now has too.
            kind == "franchises" -> Route.Franchises
            kind == "box-office" -> Route.BoxOffice
            kind == "year" -> Route.YourYear(id ?: 0)
            kind == "collection" && id != null -> Route.Collection(id)
            else -> null
        }
    }

    /** The text someone shared, for the search field to open with. */
    fun sharedQuery(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().trim()
        if (text.isBlank()) return null
        // A shared link is usually a title's page, so pull the name out of it
        // rather than searching for a URL.
        return runCatching {
            val uri = Uri.parse(text)
            if (uri.scheme?.startsWith("http") == true) {
                uri.pathSegments.lastOrNull()
                    ?.replace('-', ' ')
                    ?.replace(Regex("^\\d+ "), "")
                    ?.takeIf { it.isNotBlank() }
                    ?: text
            } else text
        }.getOrDefault(text).take(100)
    }
}
