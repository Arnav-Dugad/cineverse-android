package com.cineverse.app.data.ai

import com.cineverse.app.AppContainer
import com.cineverse.app.data.diary.Diary
import com.cineverse.app.data.model.GenreNames
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.recommend.TasteProfile
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Who the viewer is, for Gemini: everything the app knows about their taste,
 * written as a compact brief that goes in front of every personal prompt.
 *
 * Made from the account's own data - what they rated and how, what they
 * watch and when, who they keep coming back to, what they are in the middle
 * of, what is waiting on their list and where they can watch - so an answer
 * can say "you gave Interstellar a 10" rather than talk to anyone at all.
 *
 * Built at most once a minute, and again whenever the library has moved.
 */
class Persona(private val app: AppContainer) {

    private var cached: Pair<String, String>? = null
    private var builtAt = 0L

    // The brief also lives on the account (a field on the user's own
    // document), so a new phone - or this one, before the library has
    // arrived - is personal from its first question.
    @Volatile private var cloud: String? = null
    private var uploaded: String? = null
    private var uploadedAt = 0L

    private fun userDoc() = app.auth.uid.value?.let {
        com.cineverse.app.data.firebase.Firebase.firestore(app.context).collection("users").document(it)
    }

    /** Fetch the account's last brief, once, in the background. */
    fun warm() {
        if (cloud != null) return
        app.scope.launch {
            runCatching {
                // Signing in may still be under way at launch.
                kotlinx.coroutines.withTimeoutOrNull(20_000) { app.auth.uid.first { it != null } } ?: return@runCatching
                val doc = userDoc()?.get()?.await() ?: return@runCatching
                @Suppress("UNCHECKED_CAST")
                val saved = doc.get("geminiBrief") as? Map<String, Any?>
                cloud = (saved?.get("text") as? String)?.takeIf { it.isNotBlank() }
            }
        }
    }

    private fun upload(text: String) {
        val now = System.currentTimeMillis()
        if (text == uploaded || now - uploadedAt < 10 * 60_000L) return
        uploaded = text
        uploadedAt = now
        cloud = text
        app.scope.launch {
            runCatching {
                userDoc()?.set(
                    mapOf("geminiBrief" to mapOf("text" to text, "at" to now)),
                    com.google.firebase.firestore.SetOptions.merge(),
                )?.await()
            }
        }
    }

    /** The brief, or "" before there is anything to say. */
    suspend fun brief(): String {
        // Adult titles and anything in a locked list are never part of it.
        val library = app.privacy.library()
        val shows = app.privacy.shows()
        // Not the brief synced from the cloud: older versions wrote it with
        // locked and adult titles in, and before the library loads there is
        // no telling which those are.
        if (!library.loaded) return ""
        val signature = "${library.watched.size}:${library.ratings.size}:${library.saved.size}:${shows.values.sumOf { it.watchedCount }}"
        cached?.let { (sig, text) -> if (sig == signature && System.currentTimeMillis() - builtAt < 60_000) return text }

        val zone = ZoneId.systemDefault()
        val profile = TasteProfile.build(library, shows, 0)
        val name = app.auth.user.value?.name?.substringBefore(' ')?.takeIf { it.isNotBlank() }
        fun title(key: String) = library.watched[key]?.title ?: library.saved[key]?.title
        fun year(key: String) = (library.watched[key]?.year ?: library.saved[key]?.year)?.take(4)?.takeIf { it.isNotBlank() }

        val text = buildString {
            appendLine("ABOUT THE VIEWER${name?.let { " ($it)" } ?: ""}:")

            val films = library.watched.values.count { it.type == MediaType.Movie }
            val series = shows.values.count { it.watchedCount > 0 } + library.watched.values.count { it.type == MediaType.Tv && it.tmdbId !in shows }
            val ratings = library.ratings.values
            appendLine(
                "- Has watched $films films and $series series; rated ${ratings.size} titles" +
                    (if (ratings.isNotEmpty()) ", average score ${"%.1f".format(Locale.US, ratings.average())}/10" else "") + "."
            )

            profile.topGenres.take(6).map { it.name.ifBlank { GenreNames[it.id].orEmpty() } }.filter { it.isNotBlank() }
                .takeIf { it.isNotEmpty() }?.let { appendLine("- Favourite genres, strongest first: ${it.joinToString(", ")}.") }
            profile.topActors.take(6).takeIf { it.isNotEmpty() }?.let { appendLine("- Actors they watch most: ${it.joinToString(", ") { a -> a.name }}.") }
            profile.topDirectors.take(5).takeIf { it.isNotEmpty() }?.let { appendLine("- Directors they watch most: ${it.joinToString(", ") { d -> d.name }}.") }
            profile.decadeWeights.entries.sortedByDescending { it.value }.take(3).map { "${it.key}s" }
                .takeIf { it.isNotEmpty() }?.let { appendLine("- Favourite decades: ${it.joinToString(", ")}.") }
            profile.languageWeights.entries.sortedByDescending { it.value }.take(4).map { languageName(it.key) }
                .takeIf { it.isNotEmpty() }?.let { appendLine("- Languages they watch in: ${it.joinToString(", ")}.") }

            library.ratings.entries.filter { it.value >= 9 }.sortedByDescending { it.value }.take(18)
                .mapNotNull { (key, score) -> title(key)?.let { "$it${year(key)?.let { y -> " ($y)" } ?: ""} $score/10" } }
                .takeIf { it.isNotEmpty() }?.let { appendLine("- Loved: ${it.joinToString("; ")}.") }
            library.ratings.entries.filter { it.value in 1..4 }.sortedBy { it.value }.take(8)
                .mapNotNull { (key, score) -> title(key)?.let { "$it $score/10" } }
                .takeIf { it.isNotEmpty() }?.let { appendLine("- Disliked: ${it.joinToString("; ")}.") }

            val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
            library.watched.values.sortedByDescending { it.lastPlay }.take(10)
                .map { "${it.title} (${Instant.ofEpochMilli(it.lastPlay).atZone(zone).format(dateFormat)})" }
                .takeIf { it.isNotEmpty() }?.let { appendLine("- Recently watched: ${it.joinToString("; ")}.") }

            shows.values.filter { it.watchedCount > 0 && !it.complete && !it.dropped }
                .sortedByDescending { it.log.lastOrNull()?.stamp ?: it.updatedAt }.take(8)
                .map { show ->
                    val next = show.nextUp()
                    "${show.title} (${show.watchedCount} of ${show.totalEpisodes} episodes${next?.let { (s, e) -> ", next S$s E$e" } ?: ", caught up"})"
                }
                .takeIf { it.isNotEmpty() }?.let { appendLine("- In the middle of: ${it.joinToString("; ")}.") }
            shows.values.filter { it.complete }.sortedByDescending { it.completedAt }.take(6).map { it.title }
                .takeIf { it.isNotEmpty() }?.let { appendLine("- Recently finished series: ${it.joinToString(", ")}.") }

            library.saved.values.filterNot { library.isWatched(it.key) }.sortedByDescending { it.addedAt }.take(14)
                .map { "${it.title}${it.year.takeIf { y -> y.isNotBlank() }?.let { y -> " ($y)" } ?: ""}" }
                .takeIf { it.isNotEmpty() }?.let { appendLine("- On their watchlist, unwatched: ${it.joinToString("; ")}.") }
            library.lists.map { it.name }.filter { it.isNotBlank() }.take(8)
                .takeIf { it.isNotEmpty() }?.let { appendLine("- Their own lists: ${it.joinToString(", ")}.") }

            // When they watch, from the diary.
            val entries = Diary.entries(library, shows)
            if (entries.size >= 10) {
                val recent = entries.filter { it.at > System.currentTimeMillis() - 120L * 86_400_000 }.ifEmpty { entries }
                val hours = recent.groupingBy { Instant.ofEpochMilli(it.at).atZone(zone).hour }.eachCount()
                val peak = hours.maxByOrNull { it.value }?.key
                val days = recent.groupingBy { Instant.ofEpochMilli(it.at).atZone(zone).dayOfWeek }.eachCount()
                val topDay = days.maxByOrNull { it.value }?.key
                val streak = Diary.streak(Diary.days(entries).keys)
                appendLine(
                    "- Habits: mostly watches around ${peak?.let { "%02d:00".format(it) } ?: "evenings"}" +
                        (topDay?.let { ", most often on ${it.getDisplayName(TextStyle.FULL, Locale.US)}s" } ?: "") +
                        "; current streak ${streak.current} days, longest ${streak.longest}."
                )
            }

            val settings = app.settings.settings.value
            appendLine("- Lives in region ${Locale("", settings.region).getDisplayCountry(Locale.US)}; today is ${LocalDate.now()} (${LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)}).")
            if (settings.myServices.isNotEmpty()) {
                val names = runCatching { app.tmdb.streamingServices(settings.region) }.getOrDefault(emptyList())
                    .filter { it.providerId in settings.myServices }.map { it.providerName }
                if (names.isNotEmpty()) appendLine("- Subscribed to: ${names.joinToString(", ")}. Prefer what is on these.")
            }
        }.trim()

        cached = signature to text
        builtAt = System.currentTimeMillis()
        if (text.isNotBlank()) upload(text)
        return text
    }

    private fun languageName(code: String): String =
        Locale(code).getDisplayLanguage(Locale.US).takeIf { it.isNotBlank() && it != code } ?: code
}
