package com.cineverse.app

import com.cineverse.app.core.ui.ShakeDetector
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.franchise.CollectionPart
import com.cineverse.app.data.franchise.Franchises
import com.cineverse.app.data.model.CreditedItem
import com.cineverse.app.data.model.LogRow
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.model.PersonDetail
import com.cineverse.app.data.model.ShowProgress
import com.cineverse.app.data.model.WatchedItem
import com.cineverse.app.feature.person.Completion
import com.cineverse.app.feature.pick.Spin
import com.cineverse.app.feature.year.YearModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * The website's rules, pinned on the phone.
 *
 * Each of these was ported from a module on the website that already had a test
 * of its own; the cases here are the ones that decide whether a number on screen
 * is honest — a sequel that is not out yet, a skipped film, a cameo that is not a
 * role, a rewatch in a different month.
 */
class PortedLogicTest {

    private val today = LocalDate.of(2026, 10, 3)

    private fun part(id: Int, date: String) = CollectionPart(id = id, title = "Part $id", releaseDate = date)

    // ---------- franchises ----------

    @Test
    fun `an announced sequel does not count against completion`() {
        val parts = listOf(part(1, "2001-01-01"), part(2, "2004-01-01"), part(3, "2028-05-01"))
        val progress = Franchises.progress(parts, watched = setOf(1, 2), today = today)
        assertEquals(2, progress.released)
        assertEquals(1, progress.upcoming)
        assertTrue(progress.complete)
        assertEquals("Complete so far", progress.label)
    }

    @Test
    fun `an undated film you have seen counts as released`() {
        val parts = listOf(part(1, "2001-01-01"), part(2, ""))
        val seen = Franchises.progress(parts, watched = setOf(2), today = today)
        assertEquals(2, seen.released)
        assertEquals(0, seen.unknown)
        val unseen = Franchises.progress(parts, watched = emptySet(), today = today)
        assertEquals(1, unseen.released)
        assertEquals(1, unseen.unknown)
    }

    @Test
    fun `next up is the earliest released film not seen`() {
        val parts = listOf(part(3, "2010-01-01"), part(1, "2001-01-01"), part(2, "2004-01-01"))
        val progress = Franchises.progress(parts, watched = setOf(1), today = today)
        assertEquals(2, progress.nextUp?.id)
        assertEquals("1 of 3 seen", progress.label)
    }

    @Test
    fun `a gap is an unseen film before one you have seen`() {
        val ordered = Franchises.releaseOrdered(
            listOf(part(1, "2001-01-01"), part(2, "2004-01-01"), part(3, "2007-01-01"), part(4, "2010-01-01"))
        )
        val gaps = Franchises.gaps(ordered, seenIds = setOf(1, 3))
        assertEquals(listOf(2), gaps.map { it.id })
        assertTrue(Franchises.gaps(ordered, emptySet()).isEmpty())
    }

    @Test
    fun `the finish estimate averages what you have seen`() {
        val parts = listOf(part(1, "2001-01-01"), part(2, "2004-01-01"), part(3, "2007-01-01"))
        val progress = Franchises.progress(parts, watched = setOf(1, 2), today = today)
        assertEquals(110, Franchises.remainingMinutes(progress, mapOf(1 to 100, 2 to 120)))
        assertEquals(0, Franchises.remainingMinutes(progress, emptyMap()))
    }

    // ---------- shake ----------

    @Test
    fun `three hard jolts inside the window are one shake, then a cooldown`() {
        val shake = ShakeDetector()
        assertFalse(shake.sample(20f, 10f, 5f, at = 0))
        assertFalse(shake.sample(20f, 10f, 5f, at = 300))
        assertTrue(shake.sample(20f, 10f, 5f, at = 600))
        // Inside the cooldown nothing counts, however hard.
        assertFalse(shake.sample(40f, 40f, 40f, at = 900))
        assertFalse(shake.sample(40f, 40f, 40f, at = 1_000))
        assertFalse(shake.sample(40f, 40f, 40f, at = 1_100))
    }

    @Test
    fun `gravity alone and slow jolts are not a shake`() {
        val shake = ShakeDetector()
        repeat(10) { assertFalse(shake.sample(0f, 0f, 9.8f, at = it * 100L)) }
        assertFalse(shake.sample(20f, 10f, 5f, at = 0))
        assertFalse(shake.sample(20f, 10f, 5f, at = 1_500))
        assertFalse(shake.sample(20f, 10f, 5f, at = 3_000))
    }

    // ---------- pick for me ----------

    @Test
    fun `the reel always lands on the winner`() {
        for (count in 1..7) for (winner in 0 until count) {
            val reel = Spin.sequence(count, winner)
            assertEquals(winner, reel.last())
            assertTrue(reel.all { it in 0 until count })
        }
        assertTrue(Spin.sequence(0, 0).isEmpty())
    }

    @Test
    fun `the reel slows down`() {
        val delays = (0 until 16).map { Spin.frameDelay(it, 16) }
        assertEquals(45, delays.first())
        assertEquals(250, delays.last())
        assertTrue(delays.zipWithNext().all { (a, b) -> b >= a })
    }

    // ---------- completionist ----------

    private fun film(id: Int, date: String, votes: Int, rating: Double = 7.0) =
        MediaItem(id = id, type = MediaType.Movie, title = "Film $id", releaseDate = date, voteCount = votes, voteAverage = rating)

    @Test
    fun `a director's count ignores unreleased and obscure films, unless seen`() {
        val person = PersonDetail(
            id = 1, name = "Nolan", knownFor = "Directing",
            asCrew = listOf(
                CreditedItem(film(10, "2010-07-16", 30_000, 8.4), "Director"),
                CreditedItem(film(11, "2014-11-05", 35_000, 8.7), "Director"),
                CreditedItem(film(12, "2027-07-17", 0), "Director"),      // announced
                CreditedItem(film(13, "1998-04-24", 12), "Director"),     // obscure, unseen
                CreditedItem(film(14, "1997-01-01", 3), "Director"),      // obscure, but seen
                CreditedItem(film(15, "2008-07-16", 30_000), "Producer"), // not directing
            ),
        )
        val completion = Completion.of(person, watched = setOf(10, 14), today = today)
        assertEquals(Completion.Role.Director, completion.role)
        assertEquals(3, completion.total)
        assertEquals(2, completion.seen)
        assertEquals(listOf(11), completion.gaps.map { it.id })
        assertEquals("You've seen 2 of 3 Nolan films", completion.headline)
    }

    @Test
    fun `an actor's count leaves out appearances as themselves`() {
        val person = PersonDetail(
            id = 2, name = "Adam Scott", knownFor = "Acting",
            asCast = listOf(
                CreditedItem(film(20, "2013-01-01", 5_000), "Walter Mitty's boss"),
                CreditedItem(film(21, "2015-01-01", 5_000), "Himself"),
                CreditedItem(film(22, "2016-01-01", 5_000), "Guest (uncredited)"),
            ),
        )
        val completion = Completion.of(person, watched = emptySet(), today = today)
        assertEquals(1, completion.total)
    }

    // ---------- list locks ----------

    @Test
    fun `a PIN hashes exactly as the website's WebCrypto does`() = kotlinx.coroutines.runBlocking {
        // Reference computed independently with Python's hashlib.pbkdf2_hmac,
        // which is the same primitive as WebCrypto's deriveBits.
        val expected = "323247021475ee65232662a445d29691344d3b196edc512b25d5d8b539f34442"
        val salt = "9f2c4e1ab3d07788"
        assertEquals(expected, com.cineverse.app.data.lock.ListLocks.derive("2580", salt))
        assertTrue(com.cineverse.app.data.lock.ListLocks.verify("2580", salt, expected))
        assertFalse(com.cineverse.app.data.lock.ListLocks.verify("2581", salt, expected))
        assertFalse(com.cineverse.app.data.lock.ListLocks.isValidPin("123"))
        assertFalse(com.cineverse.app.data.lock.ListLocks.isValidPin("12a4"))
    }

    // ---------- your year ----------

    private fun at(date: String): Long =
        LocalDate.parse(date).atTime(20, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `a film counts once, in the month it was last watched`() {
        val library = Library(
            watched = mapOf(
                "movie_1" to WatchedItem(
                    tmdbId = 1, type = MediaType.Movie, title = "Heat", runtime = 170,
                    playDates = listOf(at("2026-01-10"), at("2026-03-02"), at("2025-12-30")),
                ),
                "movie_2" to WatchedItem(
                    tmdbId = 2, type = MediaType.Movie, title = "Ronin", runtime = 122,
                    watchedAt = at("2026-03-15"),
                ),
            ),
            loaded = true,
        )
        val summary = YearModel.summary(library, emptyMap(), 2026)
        assertEquals(2, summary.filmCount)
        assertEquals(2, summary.viewings)
        assertEquals(0, summary.filmMonths[0])
        assertEquals(2, summary.filmMonths[2])
        assertEquals(170 + 122, summary.minutes)
        assertEquals(listOf(2), summary.busiest)
        assertEquals(listOf(2026), YearModel.years(library, emptyMap()))
    }

    @Test
    fun `a series counts in the month it was finished, and dropped ones never`() {
        fun show(id: Int, done: String, dropped: Boolean = false) = ShowProgress(
            tmdbId = id, title = "Show $id", episodeRuntime = 30,
            seasons = mapOf(1 to listOf(1, 2)), structure = mapOf(1 to 2),
            log = listOf(LogRow(1, 1, at("2026-04-01"), false), LogRow(1, 2, at(done), false)),
            completedAt = at(done), dropped = dropped,
        )
        val shows = mapOf(1 to show(1, "2026-05-20"), 2 to show(2, "2026-05-21", dropped = true))
        val summary = YearModel.summary(Library(loaded = true), shows, 2026)
        assertEquals(1, summary.seriesCount)
        assertEquals(1, summary.seriesMonths[4])
        assertEquals(60, summary.minutes)
        val moments = YearModel.highlights(summary)
        assertEquals("Biggest run", moments.first { it.label == "Biggest run" }.label)
    }

    @Test
    fun `the year before is compared only when it had something`() {
        val empty = YearModel.summary(Library(loaded = true), emptyMap(), 2025)
        val current = YearModel.summary(Library(loaded = true), emptyMap(), 2026)
        assertNull(YearModel.deltas(current, empty))
    }
}
