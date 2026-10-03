package com.cineverse.app

import com.cineverse.app.core.shortcuts.HabitShortcuts
import com.cineverse.app.data.airing.Airing
import com.cineverse.app.data.airing.NextEpisode
import com.cineverse.app.data.airing.TvBrief
import com.cineverse.app.data.boxoffice.Directors
import com.cineverse.app.data.boxoffice.FilmMoney
import com.cineverse.app.data.franchise.TvFamilies
import com.cineverse.app.data.model.Episode
import com.cineverse.app.data.model.LogRow
import com.cineverse.app.data.model.ShowProgress
import com.cineverse.app.data.recap.PreviouslyOn
import com.cineverse.app.data.recap.Recaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** The rules behind this release's new features, pinned. */
class NewFeatureLogicTest {

    private val zone = ZoneId.systemDefault()
    private fun at(date: String, hour: Int = 20): Long =
        LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun brief(next: NextEpisode?, seasons: Map<Int, String> = emptyMap(), status: String = "Returning Series") =
        TvBrief(1, "Show", "Show", null, null, status, "2020-01-01", seasons, next)

    // ---------- up next ----------

    @Test
    fun `up next counts down to the second with an exact time, by day without`() {
        val now = at("2026-10-03", 12)
        val exact = Airing.upNext(brief(NextEpisode(2, 3, "", "2026-10-04", null, "")), airstamp = now + 90_061_000L, now = now)!!
        assertEquals("1d 01:01:01", Airing.countdown(exact, now))
        val dated = Airing.upNext(brief(NextEpisode(2, 3, "", LocalDate.now().plusDays(1).toString(), null, "")))!!
        assertEquals("Tomorrow", Airing.countdown(dated))
    }

    @Test
    fun `up next ignores what is far off or long out`() {
        val far = LocalDate.now().plusDays(45).toString()
        assertNull(Airing.upNext(brief(NextEpisode(1, 1, "", far, null, ""))))
        val old = LocalDate.now().minusDays(10).toString()
        assertNull(Airing.upNext(brief(NextEpisode(1, 1, "", old, null, ""))))
        val premiere = Airing.upNext(brief(NextEpisode(3, 1, "", LocalDate.now().toString(), null, "")))!!
        assertEquals("Season 3 premiere", premiere.kind)
    }

    @Test
    fun `returning is a later season premiering this month, never an ended show`() {
        val today = LocalDate.of(2026, 10, 3)
        val show = brief(null, mapOf(1 to "2024-01-01", 2 to "2026-10-20"))
        val item = Airing.returning(show, lastSeason = 1, today = today)!!
        assertEquals(2, item.season)
        assertFalse(item.out)
        assertNull(Airing.returning(show, lastSeason = 2, today = today))
        assertNull(Airing.returning(brief(null, mapOf(2 to "2026-10-20"), status = "Ended"), 1, today))
    }

    // ---------- recaps ----------

    private fun show(rows: List<LogRow>, watched: Map<Int, List<Int>>, structure: Map<Int, Int>) = ShowProgress(
        tmdbId = 9, title = "The Bear", episodeRuntime = 30, seasons = watched, structure = structure, log = rows,
    )

    @Test
    fun `a season recap reads days, binges and the best episode you watched`() {
        val rows = listOf(
            LogRow(1, 1, at("2026-09-01"), false), LogRow(1, 2, at("2026-09-01", 21), false),
            LogRow(1, 3, at("2026-09-01", 22), false), LogRow(1, 4, at("2026-09-03"), false),
        )
        val episodes = (1..4).map { Episode(it, 1, it, "E$it", overview = "x", voteAverage = 7.0 + it / 10.0, voteCount = 10) }
        val recap = Recaps.season(show(rows, mapOf(1 to listOf(1, 2, 3, 4)), mapOf(1 to 4)), 1, episodes)
        assertEquals(4, recap.episodes)
        assertEquals(3, recap.spanDays)
        assertEquals(1, recap.bingeDays)
        assertEquals(3, recap.longestSitting)
        assertEquals(4, recap.top?.number)
        assertFalse(recap.marked)
    }

    @Test
    fun `a whole season swept in one bulk mark is marked, not watched`() {
        val stamp = at("2026-09-01")
        val rows = (1..10).map { LogRow(1, it, stamp, true) }
        val recap = Recaps.season(show(rows, mapOf(1 to (1..10).toList()), mapOf(1 to 10)), 1, emptyList())
        assertTrue(recap.marked)
        assertEquals(listOf("Episodes" to "10", "Watch time" to "5h"), Recaps.seasonFigures(recap))
    }

    @Test
    fun `pace labels read like a person would say them`() {
        assertEquals("3.5 episodes a day", Recaps.paceLabel(3.5))
        assertEquals("1 episode a day", Recaps.paceLabel(1.0))
        assertEquals("2 episodes a week", Recaps.paceLabel(2.0 / 7))
    }

    @Test
    fun `weeknight evenings are a pattern, too few sittings are not`() {
        // Monday to Friday, 8pm, two weeks running.
        val weekdays = listOf("2026-09-07", "2026-09-08", "2026-09-09", "2026-09-10", "2026-09-14", "2026-09-15")
        assertEquals("on weeknights", Recaps.pattern(weekdays.map { at(it, 20) })?.phrase)
        assertNull(Recaps.pattern(weekdays.take(3).map { at(it, 20) }))
    }

    @Test
    fun `previously on never cuts a synopsis mid-thought`() {
        assertEquals("Ted arrives in London.", PreviouslyOn.firstSentence("Ted arrives in London. Rebecca has a plan."))
    }

    // ---------- tv families ----------

    @Test
    fun `a franchise is the name before a colon or a dash, strictly`() {
        assertEquals("Star Trek", TvFamilies.stem("Star Trek: Discovery"))
        assertEquals("Law & Order", TvFamilies.stem("Law & Order - Special Victims Unit"))
        assertEquals("", TvFamilies.stem("Love, Death & Robots"))
        assertTrue(TvFamilies.belongs("Law and Order: Organized Crime", "Law & Order"))
        assertFalse(TvFamilies.belongs("Love Island", "Love"))
    }

    // ---------- directors ----------

    @Test
    fun `consistency needs three films and rewards steady hits`() {
        fun film(id: Int, revenue: Long, budget: Long) =
            FilmMoney(id, "F$id", revenue = revenue, budget = budget, vote = 7.8, voteCount = 5000)
        assertNull(Directors.consistency(listOf(film(1, 900, 100), film(2, 800, 100))).score)
        val steady = Directors.consistency((1..6).map { film(it, 600_000_000, 100_000_000) })
        assertNotNull(steady.score)
        assertTrue(steady.score!! >= 70)
    }

    // ---------- habits ----------

    @Test
    fun `habit shortcuts prefer the show you watch at this hour`() {
        fun evenings(id: Int, hour: Int) = ShowProgress(
            tmdbId = id, title = "Show $id",
            seasons = mapOf(1 to listOf(1, 2, 3, 4, 5, 6)), structure = mapOf(1 to 10),
            log = (1..6).map { LogRow(1, it, at("2026-09-${10 + it}", hour), false) },
        )
        val now = ZonedDateTime.of(2026, 9, 20, 20, 30, 0, 0, zone)
        val ranked = HabitShortcuts.rank(listOf(evenings(1, 9), evenings(2, 20)), now)
        assertEquals(2, ranked.first().tmdbId)
    }
}
