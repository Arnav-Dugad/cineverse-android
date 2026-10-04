package com.cineverse.app

import com.cineverse.app.data.ai.TitleChat
import com.cineverse.app.data.cast.ActorHours
import com.cineverse.app.data.model.CreditedItem
import com.cineverse.app.data.model.Genre
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.model.Person
import com.cineverse.app.data.model.ShowProgress
import com.cineverse.app.data.model.TitleDetail
import com.cineverse.app.feature.person.Timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The rules behind 1.6's new features, pinned. */
class SixLogicTest {

    private fun credit(id: Int, date: String) =
        CreditedItem(MediaItem(id, MediaType.Movie, "Film $id", releaseDate = date), "Actor")

    @Test fun `a career groups by decade, newest first, upcoming on top`() {
        val today = LocalDate.of(2026, 10, 4)
        val groups = Timeline.byDecade(
            listOf(credit(1, "1994-05-01"), credit(2, "2019-01-01"), credit(3, "2012-06-01"), credit(4, "2027-03-01"), credit(5, "")),
            today,
        )
        assertEquals(listOf(Timeline.UPCOMING, 2010, 1990), groups.map { it.first })
        assertEquals(listOf(2, 3), groups[1].second.map { it.item.id })
        assertEquals(setOf(4, 5), groups[0].second.map { it.item.id }.toSet())
    }

    @Test fun `hours clubs follow the website`() {
        fun hours(h: Int) = ActorHours(minutes = h * 60, films = 1, shows = 0, top = emptyList())
        assertEquals(0, hours(9).club)
        assertEquals(10, hours(10).club)
        assertEquals(25, hours(37).club)
        assertEquals(100, hours(240).club)
        assertEquals(1000, hours(1200).club)
        // Half way from 25 to 50.
        assertEquals(0.5f, ActorHours(minutes = 37 * 60 + 30, films = 0, shows = 0, top = emptyList()).toNext, 0.01f)
    }

    private val show = TitleDetail(
        id = 95396, type = MediaType.Tv, title = "Severance",
        genres = listOf(Genre(18, "Drama")),
        cast = listOf(Person(1, "Adam Scott", character = "Mark")),
        crew = listOf(Person(2, "Dan Erickson", job = "Creator")),
        numberOfSeasons = 2, numberOfEpisodes = 19, episodeRuntime = 50,
    )

    @Test fun `the spoiler line follows how far you are`() {
        val chat = TitleChat
        val none = chat.spoilerLine(show, null, watched = false)
        assertTrue(none.label.startsWith("Spoiler-free"))
        val part = chat.spoilerLine(show, ShowProgress(95396, seasons = mapOf(1 to (1..9).toList(), 2 to listOf(1, 2, 3, 4))), watched = false)
        assertEquals("Spoiler-safe up to S2 E4", part.label)
        assertTrue(part.rule.contains("season 2 episode 4"))
        val film = chat.spoilerLine(show.copy(type = MediaType.Movie), null, watched = true)
        assertTrue(film.label.startsWith("You've seen it"))
    }

    @Test fun `facts answer without a model`() {
        val chat = TitleChat
        assertEquals("Severance is created by Dan Erickson.", chat.facts(show, "Who created it?"))
        assertNotNull(chat.facts(show, "who's in it")?.takeIf { it.contains("Adam Scott as Mark") })
        assertTrue(chat.facts(show, "How long are the episodes?")!!.contains("50 minutes"))
        assertNull(chat.facts(show, "What does the goat room mean?"))
    }
}
