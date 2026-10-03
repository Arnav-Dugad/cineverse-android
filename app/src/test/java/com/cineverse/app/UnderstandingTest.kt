package com.cineverse.app

import com.cineverse.app.data.ai.Ask
import com.cineverse.app.data.ai.Assistant
import com.cineverse.app.data.ai.Understanding
import com.cineverse.app.data.firebase.ContinueOrder
import com.cineverse.app.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Voice search's understanding, without Gemini: the commands and the discovery parser. */
class UnderstandingTest {

    @Test fun `commands are recognised`() {
        assertEquals(Ask.Add("Add Dune to my watchlist", "Dune"), Understanding.command("Add Dune to my watchlist"))
        assertEquals(Ask.Remove("remove The Bear from my list", "The Bear"), Understanding.command("remove The Bear from my list"))
        assertEquals("Oppenheimer", (Understanding.command("play the trailer for Oppenheimer") as Ask.Trailer).title)
        val rate = Understanding.command("rate Severance nine") as Ask.Rate
        assertEquals("Severance", rate.title)
        assertEquals(9, rate.score)
        assertEquals(8, (Understanding.command("Rate Dune 8 out of 10") as Ask.Rate).score)
        assertEquals("stats", (Understanding.command("take me to my stats") as Ask.Navigate).page)
        assertEquals("list", (Understanding.command("go to watchlist") as Ask.Navigate).page)
    }

    @Test fun `the next episode, said several ways`() {
        assertEquals("Severance", (Understanding.command("mark the next Severance episode watched") as Ask.NextEpisode).show)
        assertEquals("Severance", (Understanding.command("Mark the next episode of Severance as watched") as Ask.NextEpisode).show)
        assertEquals("The Bear", (Understanding.command("I just finished the next episode of The Bear") as Ask.NextEpisode).show)
        // A whole title is a different command.
        assertEquals("Dune", (Understanding.command("mark Dune as watched") as Ask.Watched).title)
    }

    @Test fun `a request is not a command`() {
        assertNull(Understanding.command("funny 90s movies with Tom Hanks"))
        assertNull(Understanding.command("something like Interstellar but shorter"))
    }

    @Test fun `discovery reads genres, decades and people`() {
        val q = Understanding.discover("funny 90s movies with Tom Hanks")
        assertEquals(MediaType.Movie, q.type)
        assertEquals(listOf(35), q.genres)
        assertEquals(1990, q.yearFrom)
        assertEquals(1999, q.yearTo)
        assertEquals(listOf("Tom Hanks"), q.people)
    }

    @Test fun `discovery reads language, service, runtime and comparison`() {
        val korean = Understanding.discover("Korean thrillers on Netflix")
        assertEquals("ko", korean.language)
        assertEquals("netflix", korean.provider)
        assertTrue(53 in korean.genres)

        val short = Understanding.discover("something like Interstellar but under 2 hours")
        assertEquals("interstellar", short.likeTitle)
        assertEquals(120, short.maxRuntime)

        val tv = Understanding.discover("scary sci-fi series from the 2010s")
        assertEquals(MediaType.Tv, tv.type)
        // Series use TMDB's TV genre for sci-fi.
        assertTrue(10765 in tv.genres)
        assertTrue(27 in tv.genres)
        assertEquals(2010, tv.yearFrom)
    }

    @Test fun `with friends is not a person`() {
        assertTrue(Understanding.discover("a comedy to watch with friends").people.isEmpty())
    }

    @Test fun `looks natural`() {
        assertTrue(Understanding.looksNatural("funny films from the 90s"))
        assertFalse(Understanding.looksNatural("severance"))
        assertFalse(Understanding.looksNatural("the dark knight"))
    }

    @Test fun `gemini json becomes an ask`() {
        val (ask, reply) = Understanding.fromJson(
            "k-dramas like Crash Landing",
            """```json
            {"action":"discover","type":"tv","genres":["Romance","Comedy"],"language":"ko","like":"Crash Landing on You","reply":"Korean romances like that one"}
            ```""",
        )!!
        val discover = ask as Ask.Discover
        assertEquals(MediaType.Tv, discover.query.type)
        assertEquals(listOf(10749, 35), discover.query.genres)
        assertEquals("ko", discover.query.language)
        assertEquals("Crash Landing on You", discover.query.likeTitle)
        assertEquals("Korean romances like that one", reply)

        val (next, _) = Understanding.fromJson("x", """{"action":"next_episode","title":"Severance"}""")!!
        assertEquals("Severance", (next as Ask.NextEpisode).show)
        assertNull(Understanding.fromJson("x", "not json at all"))
        assertNull(Understanding.fromJson("x", """{"action":"rate","title":"Dune"}"""))
    }

    @Test fun `title matching`() {
        assertEquals("dark knight", Assistant.normal("The Dark Knight"))
        assertEquals("amelie", Assistant.normal("Amélie"))
        assertEquals(1.0, Assistant.match(Assistant.normal("the bear"), Assistant.normal("The Bear")), 0.0)
        assertTrue(Assistant.match("severance", "severance") > Assistant.match("severance", "the severance package"))
        assertTrue(Assistant.match("dune", "dune part two") >= 0.9)
        assertEquals(0.0, Assistant.match("dune", "interstellar"), 0.0)
    }

    @Test fun `continue order pins first and hides`() {
        val order = ContinueOrder(pinned = listOf("tv_3", "tv_1"), hidden = listOf("tv_9"))
        val rows = listOf("tv_1", "tv_2", "tv_3", "tv_9", "tv_4")
        assertEquals(listOf("tv_3", "tv_1", "tv_2", "tv_4"), order.apply(rows) { it })
        assertEquals(rows, ContinueOrder().apply(rows) { it })
    }
}
