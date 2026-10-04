package com.cineverse.app.data.ai

import androidx.compose.runtime.Immutable
import com.cineverse.app.core.net.Http
import com.cineverse.app.data.model.MediaType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

/** What a sentence asked for. */
sealed interface Ask {
    val spoken: String

    data class Search(override val spoken: String, val query: String) : Ask
    data class Discover(override val spoken: String, val query: DiscoverQuery) : Ask
    data class Open(override val spoken: String, val title: String) : Ask
    data class Add(override val spoken: String, val title: String) : Ask
    data class Remove(override val spoken: String, val title: String) : Ask
    data class Watched(override val spoken: String, val title: String) : Ask
    data class Rate(override val spoken: String, val title: String, val score: Int) : Ask
    data class Trailer(override val spoken: String, val title: String) : Ask
    /** "Mark the next Severance episode watched". */
    data class NextEpisode(override val spoken: String, val show: String) : Ask
    data class Navigate(override val spoken: String, val page: String) : Ask
    /** "The guy from Severance with the beard": who someone is, from a description. */
    data class Person(override val spoken: String, val name: String, val why: String?, val alternatives: List<String>) : Ask
    /** A question that wants a written answer, not a list of titles. */
    data class Answer(override val spoken: String, val question: String) : Ask
}

/** A discover search, in words, before ids are resolved. */
@Immutable
data class DiscoverQuery(
    val type: MediaType? = null,
    val genres: List<Int> = emptyList(),
    val withoutGenres: List<Int> = emptyList(),
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val minRating: Double? = null,
    val maxRuntime: Int? = null,
    val minRuntime: Int? = null,
    val language: String? = null,
    val people: List<String> = emptyList(),
    val provider: String? = null,
    val likeTitle: String? = null,
    val keywords: List<String> = emptyList(),
    val sort: String = "popularity.desc",
) {
    /** A short, readable description: "Comedy · 1990s · with Tom Hanks". */
    fun describe(genreName: (Int) -> String?): String = buildList {
        type?.let { add(if (it == MediaType.Movie) "Films" else "Series") }
        genres.mapNotNull(genreName).forEach(::add)
        when {
            yearFrom != null && yearTo != null && yearFrom == yearTo -> add("$yearFrom")
            yearFrom != null && yearTo != null && yearTo - yearFrom == 9 && yearFrom % 10 == 0 -> add("${yearFrom}s")
            yearFrom != null && yearTo != null -> add("$yearFrom–$yearTo")
            yearFrom != null -> add("from $yearFrom")
            yearTo != null -> add("before ${yearTo + 1}")
        }
        minRating?.let { add("rated ${"%.0f".format(it)}+") }
        maxRuntime?.let { add("under ${it}m") }
        language?.let { code -> add(Understanding.Languages.entries.firstOrNull { e -> e.value == code }?.key?.replaceFirstChar { c -> c.uppercase() } ?: code) }
        people.forEach { add("with $it") }
        provider?.let { add(Understanding.ProviderNames[it] ?: it.replaceFirstChar { c -> c.uppercase() }) }
        likeTitle?.let { add("like $it") }
        keywords.forEach { add(it) }
    }.joinToString(" · ")

    val isEmpty: Boolean
        get() = genres.isEmpty() && yearFrom == null && yearTo == null && minRating == null &&
            maxRuntime == null && minRuntime == null && language == null && people.isEmpty() &&
            provider == null && likeTitle == null && keywords.isEmpty() && type == null
}

/**
 * Understanding a sentence: Gemini first, when it is there, with a parser that
 * covers the same ground when it is not.
 */
object Understanding {

    // ---------- the commands (ported from the website's voice.js grammar) ----------

    private val Numbers = mapOf("one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10)

    private val Pages = mapOf(
        "home" to "home", "homepage" to "home", "watchlist" to "list", "my list" to "list", "list" to "list",
        "watched" to "list", "stats" to "stats", "statistics" to "stats", "discover" to "discover",
        "search" to "search", "inbox" to "inbox", "notifications" to "inbox", "settings" to "settings",
        "profile" to "profile", "franchises" to "franchises", "box office" to "box-office", "my year" to "year",
        "your year" to "year", "year" to "year", "top 10" to "top10", "top ten" to "top10", "movies" to "movies",
        "films" to "movies", "tv shows" to "tv", "shows" to "tv", "series" to "tv",
    )

    private fun num(value: String): Int? = value.toIntOrNull() ?: Numbers[value.lowercase()]

    /** A command, or null when the sentence is not one. */
    fun command(raw: String): Ask? {
        val text = raw.trim().trimEnd('.', '!', '?', ',')
        if (text.isEmpty()) return null
        fun m(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE).find(text)
        m("""^(?:go|take me|navigate|open|show)\s+(?:me\s+)?(?:to\s+)?(?:my\s+|the\s+)?(home|homepage|watch\s*list|my list|list|watched|stats|statistics|discover|search|inbox|notifications|settings|profile|franchises|box office|my year|your year|top 10|top ten|movies|films|tv shows|series)$""")?.let {
            val key = it.groupValues[1].lowercase().replace(Regex("watch\\s*list"), "watchlist")
            return Ask.Navigate(text, Pages[key] ?: "home")
        }
        m("""^(?:play|show|watch|start)\s+(?:me\s+)?(?:the\s+)?trailer\s+(?:for|of)\s+(.+)$""")?.let { return Ask.Trailer(text, it.groupValues[1]) }
        m("""^(?:rate|give)\s+(.+?)\s+(\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten)(?:\s*(?:out of|/)\s*10|\s*stars?)?$""")?.let {
            val score = num(it.groupValues[2])?.coerceIn(1, 10) ?: return@let
            return Ask.Rate(text, it.groupValues[1], score)
        }
        m("""^(?:please\s+)?(?:add|save|put)\s+(.+?)\s+(?:to|in|on|into)\s+(?:my\s+)?(?:watch\s*list|list)$""")?.let { return Ask.Add(text, it.groupValues[1]) }
        m("""^(?:please\s+)?(?:remove|delete|drop)\s+(.+?)\s+(?:from|off|out of)\s+(?:my\s+)?(?:watch\s*list|list)$""")?.let { return Ask.Remove(text, it.groupValues[1]) }
        m("""^(?:please\s+)?(?:mark|tick)\s+(?:the\s+)?next\s+(?:episode\s+of\s+)?(.+?)(?:\s+episode)?\s+(?:as\s+)?watched$""")?.let { return Ask.NextEpisode(text, it.groupValues[1]) }
        m("""^i(?:'ve| have)?\s+(?:just\s+)?(?:finished|watched)\s+(?:the\s+)?(?:next|latest)\s+(?:episode\s+of\s+)?(.+?)(?:\s+episode)?$""")?.let { return Ask.NextEpisode(text, it.groupValues[1]) }
        m("""^(?:please\s+)?mark\s+(.+?)\s+(?:as\s+)?(?:watched|seen)$""")?.let { return Ask.Watched(text, it.groupValues[1]) }
        m("""^i(?:'ve| have)?\s+(?:just\s+)?(?:already\s+)?(?:watched|seen)\s+(.+)$""")?.let { return Ask.Watched(text, it.groupValues[1]) }
        m("""^(?:open|pull up|go to)\s+(?:the\s+)?(?:movie|film|show|series)?\s*(.+)$""")?.let { return Ask.Open(text, it.groupValues[1]) }
        return null
    }

    // ---------- the discovery parser ----------

    private val GenreWords: List<Pair<Regex, Int>> = listOf(
        "action|explosive|explosions" to 28, "adventures?" to 12, "animated|animation|cartoons?|anime" to 16,
        "comed(?:y|ies)|funny|hilarious|laugh|humou?r" to 35, "crime|heist|gangster|mafia" to 80,
        "documentar(?:y|ies)|docs?\\b" to 99, "drama|dramatic|emotional" to 18, "family|kids|for children" to 10751,
        "fantasy|magic(?:al)?" to 14, "histor(?:y|ical)|period" to 36, "horror|scary|spooky|terrifying|frightening" to 27,
        "music(?:al|als)?" to 10402, "myster(?:y|ies)|whodunn?its?|detective" to 9648,
        "romance|romantic|love stor(?:y|ies)|rom-?coms?" to 10749, "sci-?fi|science fiction|space|futuristic" to 878,
        "thrillers?|suspense|tense|edge of my seat" to 53, "war\\b|wartime" to 10752, "westerns?|cowboys?" to 37,
    ).map { (words, id) -> Regex("""\b(?:$words)""", RegexOption.IGNORE_CASE) to id }

    /** TMDB's TV genres differ for a few: these stand in when the type is TV. */
    private val TvSwap = mapOf(28 to 10759, 12 to 10759, 878 to 10765, 14 to 10765, 10752 to 10768)

    val Languages = linkedMapOf(
        "korean" to "ko", "k-drama" to "ko", "kdrama" to "ko", "hindi" to "hi", "bollywood" to "hi",
        "japanese" to "ja", "anime" to "ja", "spanish" to "es", "french" to "fr", "tamil" to "ta",
        "telugu" to "te", "tollywood" to "te", "malayalam" to "ml", "german" to "de", "italian" to "it",
        "chinese" to "zh", "mandarin" to "zh", "cantonese" to "cn", "turkish" to "tr", "portuguese" to "pt",
        "english" to "en", "british" to "en", "kannada" to "kn", "bengali" to "bn", "marathi" to "mr",
    )

    val Providers = linkedMapOf(
        "netflix" to 8, "prime video" to 119, "amazon prime" to 119, "prime" to 119, "disney plus" to 337, "disney+" to 337,
        "hotstar" to 122, "jiohotstar" to 2336, "apple tv" to 350, "apple tv plus" to 350, "hulu" to 15, "max" to 1899,
        "hbo" to 1899, "zee5" to 232, "sonyliv" to 237, "sony liv" to 237, "mubi" to 11, "crunchyroll" to 283, "peacock" to 386,
    )

    /** How each service writes its own name. */
    val ProviderNames = mapOf(
        "netflix" to "Netflix", "prime video" to "Prime Video", "amazon prime" to "Prime Video", "prime" to "Prime Video",
        "disney plus" to "Disney+", "disney+" to "Disney+", "hotstar" to "JioHotstar", "jiohotstar" to "JioHotstar",
        "apple tv" to "Apple TV", "apple tv plus" to "Apple TV", "hulu" to "Hulu", "max" to "HBO Max", "hbo" to "HBO Max",
        "zee5" to "ZEE5", "sonyliv" to "Sony LIV", "sony liv" to "Sony LIV", "mubi" to "MUBI", "crunchyroll" to "Crunchyroll",
        "peacock" to "Peacock",
    )

    private val WordNumbers = mapOf(
        "fifties" to 1950, "sixties" to 1960, "seventies" to 1970, "eighties" to 1980, "nineties" to 1990,
        "noughties" to 2000, "two thousands" to 2000, "twenty tens" to 2010, "twenties" to 2020,
    )

    fun discover(raw: String): DiscoverQuery {
        var text = " ${raw.lowercase()} "
        val year = LocalDate.now().year

        // "like Interstellar" / "similar to Severance" - captured first, so the
        // title's own words cannot be read as genres.
        var like: String? = null
        Regex("""\b(?:like|similar to|in the style of|in the vein of)\s+(.+?)(?=\s+(?:but|with|from|on|under|that|which|in the)\b|[,.!?]|$)""").find(text)?.let {
            like = it.groupValues[1].trim().takeIf { t -> t.length > 1 }
            text = text.replace(it.value, " ")
        }

        val people = mutableListOf<String>()
        Regex("""\b(?:with|starring|featuring|by|directed by|from director)\s+([a-z][a-z.'\-]+(?:\s+[a-z][a-z.'\-]+){1,2})""").findAll(text).forEach {
            val name = it.groupValues[1].trim()
            // "with friends", "with my family" are not people.
            if (name.split(' ').first() !in setOf("my", "the", "a", "friends", "family", "kids", "subtitles", "high", "good", "great", "lots")) {
                people += name.split(' ').joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } }
            }
        }

        val type = when {
            Regex("""\b(shows?|series|tv|seasons?|episodes?|sitcoms?|k-?dramas?|miniseries)\b""").containsMatchIn(text) -> MediaType.Tv
            Regex("""\b(movies?|films?|cinema|flicks?)\b""").containsMatchIn(text) -> MediaType.Movie
            else -> null
        }

        val genres = GenreWords.filter { (re, _) -> re.containsMatchIn(text) }.map { it.second }
            .map { if (type == MediaType.Tv) TvSwap[it] ?: it else it }.distinct()
        val without = Regex("""\b(?:no|not|without|nothing)\s+(\w+)""").findAll(text).mapNotNull { hit ->
            GenreWords.firstOrNull { (re, _) -> re.containsMatchIn(hit.groupValues[1]) }?.second
        }.toList()

        var from: Int? = null
        var to: Int? = null
        Regex("""\b(?:the\s+)?(?:(19|20)?(\d)0)'?s\b""").find(text)?.let {
            val century = it.groupValues[1].ifBlank { if (it.groupValues[2].toInt() <= 3) "20" else "19" }
            from = "$century${it.groupValues[2]}0".toInt(); to = from!! + 9
        }
        WordNumbers.entries.firstOrNull { text.contains(it.key) }?.let { from = it.value; to = it.value + 9 }
        Regex("""\b(?:after|since|from|newer than)\s+((?:19|20)\d\d)\b""").find(text)?.let { from = it.groupValues[1].toInt(); if (to != null && to!! < from!!) to = null }
        Regex("""\b(?:before|older than|until)\s+((?:19|20)\d\d)\b""").find(text)?.let { to = it.groupValues[1].toInt() - 1 }
        Regex("""\bin\s+((?:19|20)\d\d)\b""").find(text)?.let { from = it.groupValues[1].toInt(); to = from }
        if (Regex("""\b(new|recent|latest|this year|just came out)\b""").containsMatchIn(text) && from == null) { from = year - 1 }
        if (Regex("""\b(old|classic|vintage)\b""").containsMatchIn(text) && to == null) { to = 1989 }

        var rating: Double? = null
        Regex("""\b(?:above|over|at least|rated)\s+(\d(?:\.\d)?)\b""").find(text)?.let { rating = it.groupValues[1].toDouble() }
        if (rating == null && Regex("""\b(best|great|acclaimed|top rated|highly rated|masterpiece|must[- ]see|good)\b""").containsMatchIn(text)) rating = 7.5

        var maxRuntime: Int? = null
        var minRuntime: Int? = null
        Regex("""\bunder\s+(\d+(?:\.\d)?)\s*(hours?|hrs?|h|minutes?|mins?|m)\b""").find(text)?.let {
            val n = it.groupValues[1].toDouble()
            maxRuntime = if (it.groupValues[2].startsWith("h")) (n * 60).toInt() else n.toInt()
        }
        if (maxRuntime == null && Regex("""\b(short|quick)\b""").containsMatchIn(text)) maxRuntime = 95
        if (Regex("""\b(long|epic)\b""").containsMatchIn(text)) minRuntime = 150

        val language = Languages.entries.firstOrNull { Regex("""\b${Regex.escape(it.key)}\b""").containsMatchIn(text) }?.value
        val provider = Providers.keys.firstOrNull { Regex("""\bon\s+${Regex.escape(it)}\b""").containsMatchIn(text) || text.contains(" $it ") }

        val sort = when {
            Regex("""\b(newest|latest|most recent)\b""").containsMatchIn(text) -> "release"
            Regex("""\b(best|top rated|highest rated|greatest)\b""").containsMatchIn(text) -> "vote_average.desc"
            else -> "popularity.desc"
        }

        return DiscoverQuery(
            type = type,
            genres = genres.filterNot { it in without },
            withoutGenres = without,
            yearFrom = from,
            yearTo = to,
            minRating = rating,
            maxRuntime = maxRuntime,
            minRuntime = minRuntime,
            language = language,
            people = people,
            provider = provider,
            likeTitle = like,
            sort = sort,
        )
    }

    /** Does this read as a request rather than a title someone typed? */
    fun looksNatural(text: String): Boolean {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size < 3) return false
        return Regex("""\b(something|anything|movies?|films?|shows?|series|like|with|from|starring|funny|scary|best|good|recommend|under|about|set in|on netflix|on prime|90s|80s|00s|for tonight|to watch)\b""", RegexOption.IGNORE_CASE)
            .containsMatchIn(text) || isQuestion(text)
    }

    /** "Who's the guy from…", "why does…", "is it worth…": wants an answer, or a person. */
    fun isQuestion(text: String): Boolean {
        val t = text.trim().lowercase()
        if (t.split(Regex("\\s+")).size < 3) return false
        return t.endsWith("?") ||
            Regex("""^(who|whos|who's|whose|why|how|explain|tell me|should i|is it worth|what's the|what is the|what happens|what order|when does|when did|when is|where is|which actor|which actress)\b""").containsMatchIn(t) ||
            Regex("""\b(the (guy|man|woman|girl|lady|kid|actor|actress|one) (from|in|who|that|with)|that (actor|actress|guy|woman))\b""").containsMatchIn(t) ||
            Regex("""^(is|are) .{2,60} worth\b""").containsMatchIn(t)
    }

    // ---------- Gemini ----------

    fun prompt(utterance: String, today: LocalDate = LocalDate.now(), viewer: String = ""): String =
        (if (viewer.isBlank()) "" else "$viewer\n\nUse what you know about this viewer to fill in what they leave unsaid (\"something I'd like\", \"on my services\").\n\n") + """
        You are the voice and search assistant inside CineVerse, a film and TV tracking app.
        Today is $today. Turn the user's sentence into ONE JSON object, nothing else.

        Fields:
        "action": one of "search", "discover", "open", "add", "remove", "watched", "rate", "trailer", "next_episode", "navigate", "person", "answer".
          - "person": they describe or ask about a real actor or director rather than naming them ("the guy from Severance with the beard",
            "who played the Joker in The Dark Knight", "that actress from Fleabag who talks to the camera"). Identify them.
          - "answer": a question that wants a written answer rather than titles (trivia, explanations, comparisons, viewing order,
            "is The Bear worth watching", "why is Citizen Kane famous").
          - "discover": the user wants recommendations or a filtered list ("funny 90s movies with Tom Hanks", "something like Dark but shorter", "Korean thrillers on Netflix").
          - "search": they named something to look up.
          - "open": open a specific title. "add"/"remove": their watchlist. "watched": mark a title watched.
          - "rate": give a title a score 1-10. "trailer": play its trailer.
          - "next_episode": mark the next unwatched episode of a show watched ("I just finished an episode of Severance").
          - "navigate": go to a page; "page" is one of home, list, stats, discover, search, inbox, settings, franchises, box-office, year, top10, movies, tv.
        "title": the film or show named, as written on TMDB, when the action needs one.
        "score": integer 1-10 for "rate".
        "page": for "navigate".
        "query": for "search", the text to search.
        "type": "movie", "tv" or null.
        "genres": TMDB genre names from: Action, Adventure, Animation, Comedy, Crime, Documentary, Drama, Family, Fantasy, History, Horror, Music, Mystery, Romance, Science Fiction, Thriller, War, Western.
        "without_genres": genre names to exclude.
        "year_from", "year_to": integers or null.
        "min_rating": number 0-10 or null (use 7.5 for "good", "best", "acclaimed").
        "max_runtime", "min_runtime": minutes or null.
        "language": ISO 639-1 original language code or null ("bollywood" is hi, "anime" is ja).
        "people": names of actors or directors mentioned.
        "provider": streaming service named, lower case, or null.
        "like": a title the user compared to, or null.
        "keywords": up to 3 short theme words the genres cannot express ("time travel", "heist", "small town").
        "sort": "popularity.desc", "vote_average.desc" or "release".
        "person": for "person", the full name as on TMDB - your single best identification.
        "why": for "person", one short sentence connecting them to the description ("Adam Scott plays Mark Scout in Severance").
        "alternatives": for "person", up to 2 other plausible names, or [].
        "question": for "answer", the question as a clean sentence.
        "reply": one short friendly sentence describing what you understood, under 12 words.

        Sentence: "${utterance.replace("\"", "'")}"
    """.trimIndent()

    private val GenreIds = mapOf(
        "action" to 28, "adventure" to 12, "animation" to 16, "comedy" to 35, "crime" to 80, "documentary" to 99,
        "drama" to 18, "family" to 10751, "fantasy" to 14, "history" to 36, "horror" to 27, "music" to 10402,
        "mystery" to 9648, "romance" to 10749, "science fiction" to 878, "sci-fi" to 878, "thriller" to 53,
        "war" to 10752, "western" to 37,
    )

    /** Gemini's JSON as an [Ask], or null when it is unusable. Also returns the reply line. */
    fun fromJson(utterance: String, raw: String): Pair<Ask, String?>? = runCatching {
        val obj = Http.json.parseToJsonElement(Gemini.extractJson(raw) ?: return null).jsonObject
        fun str(key: String) = obj[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
        fun int(key: String) = obj[key]?.jsonPrimitive?.intOrNull
        fun list(key: String) = (obj[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
        val title = str("title")
        val reply = str("reply")
        val ask: Ask = when (str("action")) {
            "open" -> Ask.Open(utterance, title ?: return null)
            "add" -> Ask.Add(utterance, title ?: return null)
            "remove" -> Ask.Remove(utterance, title ?: return null)
            "watched" -> Ask.Watched(utterance, title ?: return null)
            "rate" -> Ask.Rate(utterance, title ?: return null, (int("score") ?: return null).coerceIn(1, 10))
            "trailer" -> Ask.Trailer(utterance, title ?: return null)
            "next_episode" -> Ask.NextEpisode(utterance, title ?: return null)
            "navigate" -> Ask.Navigate(utterance, str("page") ?: "home")
            "search" -> Ask.Search(utterance, str("query") ?: title ?: utterance)
            "person" -> Ask.Person(utterance, str("person") ?: return null, str("why"), list("alternatives").take(2))
            "answer" -> Ask.Answer(utterance, str("question") ?: utterance)
            else -> {
                val type = when (str("type")) { "movie" -> MediaType.Movie; "tv" -> MediaType.Tv; else -> null }
                Ask.Discover(
                    utterance,
                    DiscoverQuery(
                        type = type,
                        genres = list("genres").mapNotNull { GenreIds[it.lowercase()] }
                            .map { if (type == MediaType.Tv) TvSwap[it] ?: it else it }.distinct(),
                        withoutGenres = list("without_genres").mapNotNull { GenreIds[it.lowercase()] },
                        yearFrom = int("year_from"),
                        yearTo = int("year_to"),
                        minRating = obj["min_rating"]?.jsonPrimitive?.doubleOrNull,
                        maxRuntime = int("max_runtime"),
                        minRuntime = int("min_runtime"),
                        language = str("language"),
                        people = list("people"),
                        provider = str("provider")?.lowercase(),
                        likeTitle = str("like"),
                        keywords = list("keywords").take(3),
                        sort = str("sort") ?: "popularity.desc",
                    ),
                )
            }
        }
        ask to reply
    }.getOrNull()

    @Suppress("unused")
    private fun JsonObject.has(key: String) = containsKey(key)
}
