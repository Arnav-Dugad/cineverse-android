package com.cineverse.app

import com.cineverse.app.data.ai.Mention
import com.cineverse.app.data.ai.Mentions
import com.cineverse.app.data.ai.Understanding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gemini's marked names: found, shown without their marks, never half-shown. */
class MentionsTest {

    private val answer = "If you loved [[Heat (1995)]], {{Michael Mann}} also made [[Collateral (2004)]] with {{Tom Cruise}}."

    @Test fun `titles and people are found, with years`() {
        assertEquals(
            listOf(
                Mention(person = false, name = "Heat", year = "1995"),
                Mention(person = true, name = "Michael Mann"),
                Mention(person = false, name = "Collateral", year = "2004"),
                Mention(person = true, name = "Tom Cruise"),
            ),
            Mentions.parse(answer),
        )
    }

    @Test fun `the marks never reach the screen`() {
        assertEquals("If you loved Heat (1995), Michael Mann also made Collateral (2004) with Tom Cruise.", Mentions.plain(answer))
    }

    @Test fun `a mark still being written shows as its words`() {
        assertEquals("Try Interst", Mentions.plain("Try [[Interst"))
        assertEquals("Ask Christopher", Mentions.plain("Ask {{Christopher"))
    }

    @Test fun `asking for a list is understood as natural language`() {
        assertTrue(Understanding.looksNatural("make me a list of 10 slow-burn sci-fi I haven't seen"))
        assertTrue(Understanding.looksNatural("why is Heat considered a classic?"))
    }
}
