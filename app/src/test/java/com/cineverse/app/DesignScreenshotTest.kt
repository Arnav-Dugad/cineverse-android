package com.cineverse.app

import androidx.compose.foundation.layout.Arrangement
import com.cineverse.app.core.design.CvTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CineVerseTheme
import com.cineverse.app.core.ui.PosterCard
import com.cineverse.app.core.ui.ScoreRow
import com.cineverse.app.core.ui.SectionHeader
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.core.design.MotionChoice
import com.cineverse.app.core.design.ThemeChoice
import com.cineverse.app.data.scores.Scores
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The pieces, pinned.
 *
 * Not whole screens: a screen's screenshot changes every time any one of twenty
 * things does, which makes the test a nuisance that gets deleted rather than a
 * net that catches anything. These are the small components the whole app is
 * built out of, rendered at a fixed width with fixed data, so a diff here means
 * one specific thing changed and the image says which.
 *
 * Both themes, because a colour that only exists in one of them is the single
 * commonest way a design system drifts.
 *
 * Motion is OFF in every case. An animation mid-flight renders at whatever
 * progress the frame clock happened to be at, and a test that fails at random
 * is worse than no test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Pinned rather than following compileSdk: Robolectric ships a sandbox per API
// level, and a level it has not shipped yet cannot be rendered against.
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class DesignScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * A tolerance, because text does not rasterise identically everywhere.
     *
     * The references are recorded on CI's own Linux runner, which is the only
     * honest way to compare — but FreeType still produces faintly different
     * antialiasing between runner images and between font versions. The first
     * version of this test had no threshold and failed four of four on a commit
     * that changed no design at all: the diff was a scatter of single-pixel
     * specks along the edges of glyphs.
     *
     * 0.1% of pixels is far below anything a human could see and far above the
     * noise. A real change — a colour, a radius, a weight, a position — moves
     * thousands of pixels and still fails, which is the whole point.
     */
    private val OPTIONS = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.001f),
    )

    private val film = MediaItem(
        id = 27205,
        type = MediaType.Movie,
        title = "Inception",
        posterPath = null,
        voteAverage = 8.4,
        voteCount = 37_000,
        releaseDate = "2010-07-15",
    )

    private val longTitle = film.copy(
        id = 1,
        title = "The Assassination of Jesse James by the Coward Robert Ford",
        voteAverage = 7.5,
    )

    @Test
    fun posterCards_dark() = capture("poster_cards_dark", ThemeChoice.Dark) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PosterCard(film, onOpen = {}, modifier = Modifier.width(132.dp))
            PosterCard(
                longTitle,
                onOpen = {},
                modifier = Modifier.width(132.dp),
                watched = true,
                rating = 9,
            )
            PosterCard(
                film.copy(id = 2),
                onOpen = {},
                modifier = Modifier.width(132.dp),
                saved = true,
                matchPercent = 94,
            )
        }
    }

    @Test
    fun posterCards_light() = capture("poster_cards_light", ThemeChoice.Light) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PosterCard(film, onOpen = {}, modifier = Modifier.width(132.dp))
            PosterCard(
                longTitle,
                onOpen = {},
                modifier = Modifier.width(132.dp),
                watched = true,
                rating = 9,
            )
        }
    }

    @Test
    fun sectionHeaders() = capture("section_headers", ThemeChoice.Dark) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SectionHeader("Trending today")
            SectionHeader("Because you watched Severance", kicker = "For you", count = 24) {}
            SectionHeader("Continue watching", count = 7)
        }
    }

    @Test
    fun scoreRows() = capture("score_rows", ThemeChoice.Dark) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScoreRow(Scores(imdb = 8.8, rt = 87, rtAudience = 91, metacritic = 74))
            ScoreRow(Scores(imdb = 6.1))
            ScoreRow(Scores(rt = 34, metacritic = 29))
            ScoreRow(Scores.Empty)
        }
    }

    private fun capture(
        name: String,
        theme: ThemeChoice,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        compose.setContent {
            CineVerseTheme(
                theme = theme,
                dynamicColor = false,
                motion = MotionChoice.Reduced,
                haptics = null,
            ) {
                // The page colour, explicitly.
                //
                // Without it the capture lands on the test window's own white,
                // and a dark-theme screenshot comes out as near-white text on
                // near-white -- technically the right pixels for the component
                // and completely useless as a reference image.
                Box(Modifier.background(CvTheme.colors.ink)) {
                    content()
                }
            }
        }
        compose.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/$name.png",
            roborazziOptions = OPTIONS,
        )
    }
}
