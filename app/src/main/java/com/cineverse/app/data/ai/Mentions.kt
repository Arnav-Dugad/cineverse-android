package com.cineverse.app.data.ai

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import com.cineverse.app.AppContainer
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.Person
import kotlinx.coroutines.async

/** A film, series or person Gemini named in an answer. */
@Immutable
data class Mention(val person: Boolean, val name: String, val year: String? = null)

/** What the mentions in one answer turned out to be. */
@Immutable
data class Mentioned(
    val titles: List<Pair<Mention, MediaItem>> = emptyList(),
    val people: List<Pair<Mention, Person>> = emptyList(),
) {
    val isEmpty: Boolean get() = titles.isEmpty() && people.isEmpty()
    fun title(mention: Mention): MediaItem? = titles.firstOrNull { it.first.name == mention.name }?.second
    fun person(mention: Mention): Person? = people.firstOrNull { it.first.name == mention.name }?.second

    /** Open whatever a tapped name turned out to be - a title, or a person marked as one. */
    fun open(mention: Mention, onTitle: (MediaItem) -> Unit, onPerson: (Int) -> Unit) {
        val asPerson = person(mention)
        val asTitle = title(mention)
        when {
            mention.person && asPerson != null -> onPerson(asPerson.id)
            asTitle != null -> onTitle(asTitle)
            asPerson != null -> onPerson(asPerson.id)
        }
    }
}

/**
 * Names in Gemini's answers that can be tapped.
 *
 * The prompts ask Gemini to write every film or series as [[Title (Year)]]
 * and every person as {{Name}}. Those marks never reach the screen: the text
 * is shown plain, with each name set a little bolder and made a link, and
 * once the answer is finished the titles and people are found on TMDB and
 * laid out under it as posters and faces.
 */
object Mentions {

    /** For the prompts: how to mark names. */
    const val INSTRUCTION =
        "Write every film or series you name as [[Title (Year)]] and every real person you name as {{Full Name}} - " +
            "for example [[Heat (1995)]] or {{Michael Mann}}. People (actors, directors, writers) always go in {{ }}, never in [[ ]]; " +
            "only real film or series titles go in [[ ]], always with their year. Mark each one every time it appears; mark nothing else."

    // Every bracket and brace escaped: Android's ICU engine rejects a bare
    // "}}" that the desktop JVM accepts - and took the whole app with it.
    private val Marked = Regex("""\[\[([^\[\]\n]{1,120}?)\]\]|\{\{([^\{\}\n]{1,80}?)\}\}""")

    fun parse(raw: String): List<Mention> = Marked.findAll(raw).map { match ->
        match.groupValues[1].takeIf { it.isNotBlank() }?.let { title(it) }
            ?: Mention(person = true, name = match.groupValues[2].trim())
    }.filter { it.name.isNotBlank() }.distinct().toList()

    private fun title(inner: String): Mention {
        val year = Regex("""\((\d{4})\)\s*$""").find(inner)?.groupValues?.get(1)
        val name = inner.replace(Regex("""\s*\(\d{4}\)\s*$"""), "").trim()
        return Mention(person = false, name = name, year = year)
    }

    /** The answer without its marks, as plain text. Half-written marks are dropped. */
    fun plain(raw: String): String = annotated(raw, Color.Unspecified) { }.text

    /**
     * The answer as it should look: names in a slightly bolder weight and
     * tinted, each a link. A mark still being written ("[[Inter") shows as
     * its words so far, unlinked.
     */
    fun annotated(raw: String, linkColor: Color, onClick: (Mention) -> Unit): AnnotatedString = buildAnnotatedString {
        var at = 0
        val style = TextLinkStyles(SpanStyle(fontWeight = FontWeight.SemiBold, color = linkColor))
        for (match in Marked.findAll(raw)) {
            appendTail(raw.substring(at, match.range.first))
            val mention = match.groupValues[1].takeIf { it.isNotBlank() }?.let { title(it) }
                ?: Mention(person = true, name = match.groupValues[2].trim())
            val shown = if (mention.person) mention.name else match.groupValues[1].trim()
            withLink(LinkAnnotation.Clickable(tag = mention.name, styles = style) { onClick(mention) }) {
                append(shown)
            }
            at = match.range.last + 1
        }
        appendTail(raw.substring(at))
    }

    /** Text after the last whole mark: any opening still waiting for its close loses its brackets. */
    private fun AnnotatedString.Builder.appendTail(text: String) {
        append(text.replace("[[", "").replace("{{", "").replace("]]", "").replace("}}", ""))
    }

    /** Find what was named: titles (year-checked) and people, a few at a time. */
    suspend fun resolve(app: AppContainer, raw: String): Mentioned {
        val parsed = parse(raw)
        if (parsed.isEmpty()) return Mentioned()
        // Gemini now and then marks a person as a title ("[[Christopher Nolan]]"):
        // a yearless "title" that is exactly someone's name is that someone.
        val mentions = kotlinx.coroutines.coroutineScope {
            parsed.map { mention ->
                async {
                    if (mention.person || mention.year != null) return@async mention
                    val id = runCatching { app.tmdb.findPerson(mention.name) }.getOrNull() ?: return@async mention
                    val name = runCatching { app.tmdb.person(id).name }.getOrNull()
                    if (name != null && name.equals(mention.name, ignoreCase = true)) mention.copy(person = true) else mention
                }
            }.map { it.await() }
        }
        return kotlinx.coroutines.coroutineScope {
            val titles = mentions.filter { !it.person }.take(10).map { mention ->
                async {
                    val found = runCatching { app.assistant.resolve(mention.name) }.getOrNull()
                        ?.takeIf { item -> mention.year == null || item.year.isBlank() || kotlin.math.abs((item.year.toIntOrNull() ?: 0) - mention.year.toInt()) <= 1 }
                        ?: runCatching {
                            app.tmdb.searchPage(mention.name, 1, false).items.firstOrNull { item ->
                                mention.year == null || item.year == mention.year
                            }
                        }.getOrNull()
                    found?.let { mention to it }
                }
            }
            val people = mentions.filter { it.person }.take(8).map { mention ->
                async {
                    val id = runCatching { app.tmdb.findPerson(mention.name) }.getOrNull() ?: return@async null
                    val detail = runCatching { app.tmdb.person(id) }.getOrNull() ?: return@async null
                    mention to Person(id = id, name = detail.name, profilePath = detail.profilePath, job = detail.knownFor)
                }
            }
            Mentioned(
                titles = titles.mapNotNull { it.await() }.filterNot { it.second.adult || app.privacy.isAdult(it.second.key) }.distinctBy { it.second.key },
                people = people.mapNotNull { it.await() }.distinctBy { it.second.id },
            )
        }
    }
}
