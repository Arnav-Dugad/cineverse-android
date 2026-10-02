package com.cineverse.app.data.model

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Opening a title where it actually plays.
 *
 * The website can only send you to a provider's web search, because a browser
 * has nowhere better to go. A phone does: Netflix, Prime Video and the rest are
 * almost certainly INSTALLED, and sending someone to netflix.com on a phone that
 * has the Netflix app is the kind of small wrongness that makes an app feel like
 * a website in a costume.
 *
 * So each provider carries three things, tried in order:
 *
 *  1. a **deep link** the provider's own app registers, where one exists and is
 *     documented — these open the app directly on a search for the title;
 *  2. the **package name**, so the app can be launched even when the deep link
 *     is not honoured;
 *  3. the **web search**, the website's own URL, which works everywhere.
 *
 * Matching is on a substring of TMDB's `provider_name`, case-insensitively, and
 * in the website's order so the two never disagree about which rule wins. The
 * last resort is the same as the website's: TMDB's region-level JustWatch page,
 * then a plain web search — never a dead end.
 */
data class ProviderTarget(
    /** Matched against TMDB's provider name. */
    val match: Regex,
    val packageName: String? = null,
    /** Builds a deep link the provider's app understands, given the title. */
    val appLink: ((String) -> String)? = null,
    /** The website's own fallback. */
    val webSearch: (String) -> String,
)

object Providers {

    private val TARGETS = listOf(
        ProviderTarget(
            match = Regex("netflix", RegexOption.IGNORE_CASE),
            packageName = "com.netflix.mediaclient",
            appLink = { "https://www.netflix.com/search?q=$it" },
            webSearch = { "https://www.netflix.com/search?q=$it" },
        ),
        ProviderTarget(
            match = Regex("prime video|amazon", RegexOption.IGNORE_CASE),
            packageName = "com.amazon.avod.thirdpartyclient",
            appLink = { "https://app.primevideo.com/search?phrase=$it" },
            webSearch = { "https://www.primevideo.com/search/?phrase=$it" },
        ),
        ProviderTarget(
            match = Regex("jiohotstar|hotstar", RegexOption.IGNORE_CASE),
            packageName = "in.startv.hotstar",
            appLink = { "https://www.hotstar.com/in/explore?search_query=$it" },
            webSearch = { "https://www.hotstar.com/in/explore?search_query=$it" },
        ),
        ProviderTarget(
            match = Regex("jiocinema", RegexOption.IGNORE_CASE),
            packageName = "com.jio.media.ondemand",
            webSearch = { "https://www.jiocinema.com/search/$it" },
        ),
        ProviderTarget(
            match = Regex("disney", RegexOption.IGNORE_CASE),
            packageName = "com.disney.disneyplus",
            appLink = { "https://www.disneyplus.com/search?q=$it" },
            webSearch = { "https://www.disneyplus.com/search?q=$it" },
        ),
        ProviderTarget(
            match = Regex("apple tv|itunes", RegexOption.IGNORE_CASE),
            packageName = "com.apple.atve.androidtv.appletv",
            appLink = { "https://tv.apple.com/search?term=$it" },
            webSearch = { "https://tv.apple.com/search?term=$it" },
        ),
        ProviderTarget(
            match = Regex("crunchyroll", RegexOption.IGNORE_CASE),
            packageName = "com.crunchyroll.crunchyroid",
            appLink = { "https://www.crunchyroll.com/search?q=$it" },
            webSearch = { "https://www.crunchyroll.com/search?q=$it" },
        ),
        ProviderTarget(
            match = Regex("hulu", RegexOption.IGNORE_CASE),
            packageName = "com.hulu.plus",
            webSearch = { "https://www.hulu.com/search?q=$it" },
        ),
        ProviderTarget(
            match = Regex("hbo|max\\b", RegexOption.IGNORE_CASE),
            packageName = "com.wbd.stream",
            webSearch = { "https://www.max.com/search?q=$it" },
        ),
        ProviderTarget(
            match = Regex("paramount", RegexOption.IGNORE_CASE),
            packageName = "com.cbs.app",
            webSearch = { "https://www.paramountplus.com/search/?query=$it" },
        ),
        ProviderTarget(
            match = Regex("peacock", RegexOption.IGNORE_CASE),
            packageName = "com.peacocktv.peacockandroid",
            webSearch = { "https://www.peacocktv.com/search?q=$it" },
        ),
        ProviderTarget(
            match = Regex("zee5|zee tv|zeetv", RegexOption.IGNORE_CASE),
            packageName = "com.graymatrix.did",
            webSearch = { "https://www.zee5.com/search?q=$it" },
        ),
        ProviderTarget(
            match = Regex("sonyliv", RegexOption.IGNORE_CASE),
            packageName = "com.sonyliv",
            webSearch = { "https://www.sonyliv.com/search?searchTerm=$it" },
        ),
        ProviderTarget(
            match = Regex("mubi", RegexOption.IGNORE_CASE),
            packageName = "com.mubi",
            webSearch = { "https://mubi.com/en/search/films?query=$it" },
        ),
        ProviderTarget(
            match = Regex("aha", RegexOption.IGNORE_CASE),
            packageName = "com.aha.android",
            webSearch = { "https://www.aha.video/search?q=$it" },
        ),
        ProviderTarget(
            match = Regex("sun ?nxt", RegexOption.IGNORE_CASE),
            packageName = "com.suntv.sunnxt",
            webSearch = { "https://www.sunnxt.com/search/$it" },
        ),
        ProviderTarget(
            match = Regex("youtube", RegexOption.IGNORE_CASE),
            packageName = "com.google.android.youtube",
            appLink = { "https://www.youtube.com/results?search_query=$it" },
            webSearch = { "https://www.youtube.com/results?search_query=$it" },
        ),
        ProviderTarget(
            match = Regex("google play", RegexOption.IGNORE_CASE),
            packageName = "com.google.android.videos",
            appLink = { "https://play.google.com/store/search?q=$it&c=movies" },
            webSearch = { "https://play.google.com/store/search?q=$it&c=movies" },
        ),
    )

    fun targetFor(providerName: String): ProviderTarget? =
        TARGETS.firstOrNull { it.match.containsMatchIn(providerName) }

    /** Is the provider's own app on this phone? Drives the "Open app" wording. */
    fun isInstalled(context: Context, providerName: String): Boolean {
        val target = targetFor(providerName) ?: return false
        val packageName = target.packageName ?: return false
        return runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }

    /**
     * Go and watch it.
     *
     * The order matters and each step is a real fallback, not a formality:
     * the installed app first, then the provider's site, then TMDB's own
     * JustWatch page for the region, then a plain search. Something always
     * opens — a "Where to watch" button that does nothing when tapped is worse
     * than no button at all.
     */
    fun open(
        context: Context,
        providerName: String,
        title: String,
        regionLink: String = "",
    ) {
        val query = Uri.encode(title)
        val target = targetFor(providerName)
        val packageName = target?.packageName

        // 1. The provider's own app, pointed at the title.
        if (packageName != null && installed(context, packageName)) {
            val deepLink = target.appLink?.invoke(query)
            if (deepLink != null) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
                    .setPackage(packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) != null &&
                    runCatching { context.startActivity(intent); true }.getOrDefault(false)
                ) return
            }
            // 2. The app, even if it would not take the link.
            val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launch != null &&
                runCatching { context.startActivity(launch); true }.getOrDefault(false)
            ) return
        }

        // 3. The provider's website, then TMDB's JustWatch page, then a search.
        val url = target?.webSearch?.invoke(query)
            ?: regionLink.takeIf { it.isNotBlank() }
            ?: "https://www.google.com/search?q=" +
            Uri.encode("watch $title on $providerName")
        openUrl(context, url)
    }

    private fun installed(context: Context, packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    private fun openUrl(context: Context, url: String) {
        val uri = Uri.parse(url)
        runCatching {
            androidx.browser.customtabs.CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(context, uri)
        }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}
