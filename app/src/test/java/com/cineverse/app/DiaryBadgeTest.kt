package com.cineverse.app

import com.cineverse.app.data.badges.BadgeContext
import com.cineverse.app.data.badges.Badges
import com.cineverse.app.data.diary.Diary
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.LogRow
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.model.ShowProgress
import com.cineverse.app.data.model.WatchedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** The Diary's rules and the badges', pinned against the website's. */
class DiaryBadgeTest {

    private val zone = ZoneId.systemDefault()
    private fun at(date: String, hour: Int = 21) = LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun `every play is a day, and a rewatch knows its first viewing`() {
        val film = WatchedItem(
            tmdbId = 1, type = MediaType.Movie, title = "Heat", runtime = 170,
            playDates = listOf(at("2024-03-01"), at("2026-10-02")), plays = 2,
        )
        val entries = Diary.entries(Library(watched = mapOf(film.key to film)), emptyMap())
        assertEquals(2, entries.size)
        val rewatch = entries.last()
        assertTrue(rewatch.rewatch)
        assertEquals(2, rewatch.viewing)
        assertEquals(at("2024-03-01"), rewatch.allViewings.first())
    }

    @Test fun `bulk marks are bookkeeping, not viewing`() {
        val show = ShowProgress(
            tmdbId = 9, title = "Show", episodeRuntime = 30,
            log = listOf(
                LogRow(1, 1, at("2026-10-01"), bulk = false),
                LogRow(1, 2, at("2026-10-01", 22), bulk = false),
                LogRow(2, 1, at("2026-10-03"), bulk = true),
            ),
        )
        val days = Diary.days(Diary.entries(Library(), mapOf(9 to show)))
        assertEquals(setOf(LocalDate.parse("2026-10-01")), days.keys)
        assertEquals(60, days.values.first().minutes)
    }

    @Test fun `streaks count today, or yesterday when today is still empty`() {
        val today = LocalDate.parse("2026-10-04")
        val days = setOf("2026-10-01", "2026-10-02", "2026-10-03").map(LocalDate::parse).toSet()
        val streak = Diary.streak(days, today)
        assertEquals(3, streak.current)
        assertEquals(false, streak.todayActive)
        assertEquals(LocalDate.parse("2026-10-01"), streak.start)
        val lit = Diary.streak(days + today, today)
        assertEquals(4, lit.current)
        assertEquals(4, lit.longest)
    }

    @Test fun `a milestone is announced once, on the day it is reached`() {
        val today = LocalDate.parse("2026-10-07")
        val days = (0L..6L).map { today.minusDays(it) }.toSet()
        val streak = Diary.streak(days, today)
        assertEquals(7, Diary.milestoneToday(streak, emptySet()))
        assertNull(Diary.milestoneToday(streak, setOf("${streak.start}:7")))
        assertNull(Diary.milestoneToday(Diary.streak(days - today, today), emptySet()))
    }

    @Test fun `badges follow the website's thresholds`() {
        val watched = (1..100).associate { id ->
            val item = WatchedItem(tmdbId = id, type = MediaType.Movie, title = "F$id", runtime = 120, genres = listOf(18), year = "1999")
            item.key to item
        }
        val context = BadgeContext.of(Library(watched = watched, ratings = mapOf("movie_1" to 10)))
        val earned = Badges.earned(context)
        assertTrue("watch_50" in earned)
        assertTrue("first_watch" in earned)
        assertTrue("perfect_10" in earned)
        assertTrue("hours_100" !in earned || context.hours >= 250)
        assertEquals(200, context.hours)
    }
}
