package com.cineverse.app.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.InsertChart
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.InsertChart
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector
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
    @Serializable data object Settings : Route
    @Serializable data object Auth : Route
    @Serializable data object Releases : Route
    @Serializable data object Franchises : Route
    @Serializable data object BoxOffice : Route

    @Serializable data class Detail(val id: Int, val type: String) : Route {
        val mediaType: MediaType get() = MediaType.of(type)
    }

    @Serializable data class Person(val id: Int) : Route
    @Serializable data class Collection(val id: Int) : Route

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
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    Home(Route.Home, "Home", Icons.Rounded.Home, Icons.Outlined.Home),
    Discover(Route.Discover, "Discover", Icons.Rounded.Explore, Icons.Outlined.Explore),
    MyList(Route.MyList, "My List", Icons.Rounded.Bookmarks, Icons.Outlined.Bookmarks),
    Stats(Route.Stats, "Stats", Icons.Rounded.InsertChart, Icons.Outlined.InsertChart),
    Profile(Route.Profile, "You", Icons.Rounded.Person, Icons.Outlined.Person),
}
