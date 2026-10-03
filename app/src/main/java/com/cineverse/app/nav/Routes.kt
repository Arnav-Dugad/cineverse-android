package com.cineverse.app.nav

import com.cineverse.app.core.ui.TabGlyph
import com.cineverse.app.data.model.MediaType
import kotlinx.serialization.Serializable

/**
 * Where the app can be.
 *
 * Type-safe routes: a destination is a data class, not a string with holes in
 * it, so a navigation that does not compile cannot ship. The arguments are the
 * minimum needed to open a screen cold — an id and a type — because every one of
 * these has to survive process death and a deep link from outside the app.
 */
sealed interface Route {

    @Serializable data object Home : Route
    @Serializable data object Discover : Route
    @Serializable data object MyList : Route
    @Serializable data object Stats : Route
    @Serializable data object Profile : Route

    @Serializable data object Search : Route
    /** Search, listening from the moment it opens. */
    @Serializable data object VoiceSearch : Route
    @Serializable data object Settings : Route
    @Serializable data object Auth : Route
    @Serializable data object Releases : Route
    @Serializable data object Franchises : Route
    @Serializable data object BoxOffice : Route

    @Serializable data class Detail(val id: Int, val type: String) : Route {
        val mediaType: MediaType get() = MediaType.of(type)
    }

    @Serializable data class Person(val id: Int) : Route

    /** A trailer, played in the app. The title is only there for the chrome. */
    @Serializable data class Trailer(val key: String, val title: String) : Route
    @Serializable data class Collection(val id: Int) : Route

    @Serializable data object Inbox : Route

    /** The chart: films or series. */
    @Serializable data class TopTen(val type: String = "movie") : Route

    /** A studio's or a network's whole catalogue. */
    @Serializable data class Studio(val id: Int, val network: Boolean, val name: String) : Route

    /** Your year in films and finished series. Zero means "the sensible one". */
    @Serializable data class YourYear(val year: Int = 0) : Route

    /** A full-screen grid: "Top rated films", a genre, a studio's catalogue. */
    @Serializable data class Browse(
        val title: String,
        val type: String,
        val list: String = "",
        val genre: Int = 0,
        val sort: String = "popularity.desc",
    ) : Route
}

/**
 * The five tabs.
 *
 * Nine nav items on the website become five here, because a bottom bar holds
 * five before the labels start lying. Discover absorbs Movies, TV Shows,
 * Franchises, Box Office and Releases — five pages that are all the same verb —
 * and Search is a mode in the top bar rather than a place, which is where IMDb,
 * YouTube and Prime all ended up.
 */
enum class Tab(
    val route: Route,
    val label: String,
    val glyph: TabGlyph,
) {
    Home(Route.Home, "Home", TabGlyph.Home),
    Discover(Route.Discover, "Discover", TabGlyph.Discover),
    MyList(Route.MyList, "My List", TabGlyph.List),
    Stats(Route.Stats, "Stats", TabGlyph.Stats),
    Profile(Route.Profile, "You", TabGlyph.Person),
}
