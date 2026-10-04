package com.cineverse.app.nav

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavGraphBuilder
import com.cineverse.app.core.ui.LocalNavAnimatedScope
import com.cineverse.app.core.ui.LocalSharedTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateDp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.toRoute
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.graphicsLayer
import com.cineverse.app.core.ui.LocalPinnedBarLift
import com.cineverse.app.core.ui.movingLights
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cineverse.app.AppContainer
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Motion
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.feature.auth.AuthScreen
import com.cineverse.app.feature.browse.BrowseScreen
import com.cineverse.app.feature.browse.BrowseViewModel
import com.cineverse.app.feature.detail.DetailScreen
import com.cineverse.app.feature.detail.DetailViewModel
import com.cineverse.app.feature.discover.DiscoverScreen
import com.cineverse.app.feature.discover.DiscoverViewModel
import com.cineverse.app.feature.home.HomeScreen
import com.cineverse.app.feature.home.HomeViewModel
import com.cineverse.app.feature.list.MyListScreen
import com.cineverse.app.feature.list.MyListViewModel
import com.cineverse.app.feature.person.PersonScreen
import com.cineverse.app.feature.person.PersonViewModel
import com.cineverse.app.feature.profile.ProfileScreen
import com.cineverse.app.feature.profile.SettingsScreen
import com.cineverse.app.feature.profile.ProfileViewModel
import com.cineverse.app.feature.search.SearchScreen
import com.cineverse.app.feature.search.SearchViewModel
import com.cineverse.app.feature.stats.StatsScreen
import com.cineverse.app.feature.stats.StatsViewModel
import com.cineverse.app.feature.trailer.TrailerScreen
import com.cineverse.app.update.UpdateSheet
import com.cineverse.app.update.WhatsNewSheet
import com.cineverse.app.update.VersionHistorySheet
import com.cineverse.app.update.UpdateState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * One factory for every view model in the app.
 *
 * With manual dependency injection a view model is just a constructor call, and
 * this is the one line of ceremony Compose needs to make that happen. The
 * alternative — an annotation processor and a generated component — would be
 * more machinery than the whole graph.
 */
@Composable
inline fun <reified T : ViewModel> cvViewModel(
    key: String? = null,
    crossinline create: () -> T,
): T = viewModel(
    key = key,
    factory = viewModelFactory { initializer { create() } },
)

/**
 * A destination that can take part in a shared-element transition.
 *
 * Every screen needs its own [AnimatedVisibilityScope] for a poster to travel
 * into or out of it, and that scope is only available as the receiver of the
 * destination lambda. Wrapping `composable` once here is the difference between
 * one line of ceremony in one place and the same provider pasted into ten.
 */
private inline fun <reified T : Any> NavGraphBuilder.cvComposable(
    crossinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable<T> { entry ->
    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
        // While it moves, a page is a card: rounded corners, a shadow along
        // its edge and its own background, so the page beneath never shows
        // through it. At rest it is square and flat again.
        val corner by transition.animateDp(
            transitionSpec = { tween(CARD_MS, easing = CardEasing) },
            label = "cardCorner",
        ) { state -> if (state == androidx.compose.animation.EnterExitState.Visible) 0.dp else 26.dp }
        val ink = CvTheme.colors.ink
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (corner > 0.dp) {
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(corner)
                        clip = true
                        shadowElevation = 18.dp.toPx() * (corner / 26.dp)
                    }
                }
                .background(ink)
        ) {
            content(entry)
        }
    }
}

/** The tabs that open on a hero, whose top bar starts as a scrim over the art. */
private val HeroTabs = setOf(Tab.Home, Tab.Movies, Tab.TvShows)

private fun androidx.navigation.NavDestination.isTab(): Boolean =
    hierarchy.any { node -> Tab.entries.any { node.hasRoute(it.route::class) } }

/** Pages that are the whole screen, with or without a pinned bar. */
private fun androidx.navigation.NavDestination.isFullScreen(): Boolean =
    hasRoute(Route.Trailer::class) || hasRoute(Route.Auth::class) ||
        hasRoute(Route.Search::class) || hasRoute(Route.VoiceSearch::class)

@Composable
fun CineVerseNav(
    app: AppContainer,
    deepLink: Route? = null,
    onDeepLinkHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val bars = remember { BarVisibility() }
    val snackbars = remember { SnackbarHostState() }
    val colors = CvTheme.colors
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showUpdate by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var loadingHistory by remember { mutableStateOf(false) }
    val update by app.updates.state.collectAsStateWithLifecycle()
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    // One peek for the whole app. Hosted here rather than inside a screen,
    // because a sheet raised from inside a LazyRow item dies the moment that
    // item scrolls out of the composition.
    val peek = com.cineverse.app.feature.sheets.rememberPeekHost(app)
    var rateTarget by remember { mutableStateOf<MediaItem?>(null) }
    val history by app.updates.history.collectAsStateWithLifecycle()
    val shelf by app.library.library.collectAsStateWithLifecycle()
    val inboxCount by app.inbox.unread.collectAsStateWithLifecycle()
    val signedInUid by app.auth.uid.collectAsStateWithLifecycle()
    // -1 until the library has loaded, so its arrival is not mistaken for a save.
    var acknowledgedSaved by remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    val savedWaiting = if (shelf.loaded) shelf.saved.keys.count { it !in shelf.watched } else -1
    val whatsNew by app.updates.whatsNew.collectAsStateWithLifecycle()

    // Anything the app needs to say out loud — an OMDb key that stopped working,
    // a sweep that finished — arrives here rather than in a dozen screens.
    LaunchedEffect(Unit) {
        app.messages.collectLatest { message -> snackbars.showSnackbar(message) }
    }

    // The tab whose stack the page on screen sits in. Recorded here, where a
    // tab is entered, rather than read off the screen: switching to a tab
    // whose saved stack ends on a pushed page never shows the tab itself.
    var hostTab by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(
            when (settings.startTab) {
                "discover" -> Tab.Discover
                "list" -> Tab.MyList
                "stats" -> Tab.Stats
                else -> Tab.Home
            }
        )
    }

    // Switching tab, the one way it is ever done: the bar and a link that
    // names a tab both come through here.
    fun goToTab(tab: Tab) {
        hostTab = tab
        navController.navigate(tab.route) {
            popUpTo(Route.Home) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // A shortcut, a widget row or a shared link, taken once.
    //
    // A link to a TAB switches to it, exactly as the bar does. It used to be
    // pushed on top of whatever tab was showing, so the You tab's saved stack
    // became "You, then Stats" - and from then on tapping You restored that
    // stack and left you looking at Stats, with the tab apparently dead.
    LaunchedEffect(deepLink) {
        val route = deepLink ?: return@LaunchedEffect
        val tab = Tab.entries.firstOrNull { it.route == route }
        when {
            tab == null -> navController.navigate(route) { launchSingleTop = true }
            // Already inside that tab, on a page pushed over it: back to the
            // tab's own front page. Switching would restore the stack it is
            // already in and land on the very page the link came from.
            navController.currentDestination?.isTab() == false && hostTab == tab &&
                navController.popBackStack(tab.route, inclusive = false) -> Unit
            else -> goToTab(tab)
        }
        onDeepLinkHandled()
    }

    // The update check, once every six hours, never on the critical path.
    LaunchedEffect(Unit) {
        val result = app.updates.check()
        if (result is UpdateState.Available) showUpdate = true
    }

    // Did this launch follow an install? If so, say what landed.
    LaunchedEffect(Unit) { app.updates.checkWhatsNew() }

    // Opening the history fetches it if the session has not already.
    LaunchedEffect(showHistory) {
        if (!showHistory) return@LaunchedEffect
        loadingHistory = true
        app.updates.loadHistory()
        loadingHistory = false
    }

    val currentTab = Tab.entries.firstOrNull { tab ->
        backStack?.destination?.hierarchy?.any { node -> node.hasRoute(tab.route::class) } == true
    }

    // With the bar pinned it stays on pushed pages too, showing the tab they
    // were opened from; tapping that tab again comes back to its front page.
    LaunchedEffect(currentTab) { currentTab?.let { hostTab = it } }
    val barTab = currentTab
        ?: hostTab.takeIf { settings.pinNavBar && backStack?.destination?.isFullScreen() == false }
    androidx.compose.runtime.SideEffect { bars.pinned = settings.pinNavBar }
    var barLift by remember { mutableStateOf(0.dp) }
    // The tab a bar shows while it fades away, once there is no tab under it.
    var lastShownTab by remember { mutableStateOf(Tab.Home) }
    androidx.compose.runtime.SideEffect { (barTab ?: currentTab)?.let { lastShownTab = it } }

    // Every arrival starts with the bar showing. Scrolling a pushed page (Your
    // Year, a title) tucks it away, and without this you came back to a tab
    // with no way to leave it until you happened to scroll up.
    LaunchedEffect(backStack?.id) { bars.show() }

    // The bar is a scrim over a front page's hero and glass once the hero has
    // gone - per tab, so a scrolled Movies does not darken Home's bar.
    val heroScrolled = remember { androidx.compose.runtime.mutableStateMapOf<Tab, Boolean>() }
    // How far Stats' large title has folded into the top bar.
    var statsTitle by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

    Scaffold(
        containerColor = colors.ink,
        contentColor = colors.text,
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            // Only over a tab. A pushed screen carries its own back arrow, and
            // two bars stacked is the fastest way to waste a phone's height.
            // It fades with the page rather than popping in as a card lands.
            androidx.compose.animation.AnimatedVisibility(
                visible = currentTab != null,
                enter = fadeIn(tween(CARD_MS)),
                exit = fadeOut(tween(Motion.Quick)),
            ) {
                val tab = currentTab ?: lastShownTab
                CvTopBar(
                    tab = tab,
                    onSearch = { navController.navigate(Route.Search) },
                    overArt = tab in HeroTabs && heroScrolled[tab] != true,
                    inboxCount = if (shelf.loaded && signedInUid != null) inboxCount else 0,
                    onInbox = { navController.navigate(Route.Inbox) },
                    titleAlpha = if (tab == Tab.Stats) statsTitle else 1f,
                )
            }
        },
        bottomBar = {
            androidx.compose.animation.AnimatedVisibility(
                visible = barTab != null,
                enter = fadeIn(tween(CARD_MS)) + androidx.compose.animation.slideInVertically(tween(CARD_MS, easing = CardEasing)) { it / 3 },
                exit = fadeOut(tween(Motion.Quick)) + androidx.compose.animation.slideOutVertically(tween(CARD_MS, easing = CardEasing)) { it / 3 },
            ) {
                CvNavigationBar(
                    current = barTab ?: lastShownTab,
                    bars = bars,
                    // Titles saved and not yet watched: the one number worth a tab.
                    savedCount = savedWaiting,
                    acknowledgedSaved = acknowledgedSaved,
                    onAcknowledgeSaved = { acknowledgedSaved = it },
                    onSelect = { tab ->
                        bars.show()
                        // The tab this page sits in: back to its front page.
                        val home = currentTab == null && tab == barTab && navController.popBackStack(tab.route, inclusive = false)
                        if (!home) goToTab(tab)
                    },
                )
            }
        },
    ) { padding ->
        // Remembered rather than read live, so a page does not jump when the
        // bar steps aside for a trailer.
        val bottom = padding.calculateBottomPadding()
        androidx.compose.runtime.SideEffect { if (barTab != null && bottom > 0.dp) barLift = bottom }
        val open: (MediaItem) -> Unit = { item ->
            navController.navigate(Route.Detail(item.id, item.type.wire))
        }

        // One shared-transition layout around the whole graph. It has to be
        // OUTSIDE the NavHost: the two ends of a travelling poster live in
        // different destinations, and a scope that only exists inside one of
        // them can never match them up.
        SharedTransitionLayout {
        CompositionLocalProvider(
            LocalSharedTransitionScope provides this,
            LocalPinnedBarLift provides if (settings.pinNavBar) barLift else 0.dp,
        ) {
        NavHost(
            navController = navController,
            // The tab the user chose, not always Home. This read Route.Home
            // unconditionally, so "Open on" stored a value and changed nothing.
            startDestination = when (settings.startTab) {
                "discover" -> Route.Discover
                "list" -> Route.MyList
                "stats" -> Route.Stats
                else -> Route.Home
            },
            modifier = Modifier
                .fillMaxSize()
                .background(colors.ink)
                .nestedScroll(bars.connection)
                .movingLights(settings.movingLights),
            // Tabs cross-fade; anything pushed on top slides in from the side,
            // so the hierarchy is legible from the motion alone.
            enterTransition = { pushEnter() },
            exitTransition = { pushExit() },
            popEnterTransition = { popEnter() },
            popExitTransition = { popExit() },
        ) {
            cvComposable<Route.Home> {
                // "Ask CineVerse" floats over Home, folding to its spark while
                // the page is scrolled down and opening out on the way back up.
                var askExpanded by remember { mutableStateOf(true) }
                var tonight by remember { mutableStateOf(false) }
                val askScroll = remember {
                    object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                        override fun onPreScroll(
                            available: androidx.compose.ui.geometry.Offset,
                            source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
                        ): androidx.compose.ui.geometry.Offset {
                            if (available.y < -6f) askExpanded = false else if (available.y > 6f) askExpanded = true
                            return androidx.compose.ui.geometry.Offset.Zero
                        }
                    }
                }
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().nestedScroll(askScroll)) {
                HomeScreen(
                    viewModel = cvViewModel("home") { HomeViewModel(app) },
                    onOpen = open,
                    onBrowse = { navController.navigate(it) },
                    onContinue = { row ->
                        navController.navigate(Route.Detail(row.item.id, row.item.type.wire))
                    },
                    onPeek = peek::open,
                    contentPadding = padding,
                    onScrolledPastHero = { heroScrolled[Tab.Home] = it },
                )
                if (settings.geminiOn) {
                    com.cineverse.app.feature.home.AskCineVerseFab(
                        expanded = askExpanded,
                        onText = { navController.navigate(Route.Search) { launchSingleTop = true } },
                        onVoice = { navController.navigate(Route.VoiceSearch) { launchSingleTop = true } },
                        onTonight = { tonight = true },
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.BottomEnd)
                            .padding(end = 18.dp, bottom = padding.calculateBottomPadding() + 16.dp)
                            // Rides down with the bar when it tucks away.
                            .graphicsLayer { translationY = bars.hidden * (padding.calculateBottomPadding().toPx() - 16.dp.toPx()).coerceAtLeast(0f) },
                    )
                }
                }
                if (tonight) {
                    val home = cvViewModel("home") { HomeViewModel(app) }
                    com.cineverse.app.feature.home.TonightSheet(
                        pick = home::pickTonight,
                        onOpen = open,
                        onStart = home::startWatching,
                        onDismiss = { tonight = false },
                    )
                }
            }

            // The two catalogues, each a tab of its own.
            for ((tab, type) in listOf(Tab.Movies to MediaType.Movie, Tab.TvShows to MediaType.Tv)) {
                val content: @Composable () -> Unit = {
                    com.cineverse.app.feature.catalog.CatalogScreen(
                        viewModel = cvViewModel("catalog_${type.wire}") {
                            com.cineverse.app.feature.catalog.CatalogViewModel(app, type)
                        },
                        onOpen = open,
                        onPeek = peek::open,
                        contentPadding = padding,
                        onScrolledPastHero = { heroScrolled[tab] = it },
                    )
                }
                if (tab == Tab.Movies) cvComposable<Route.Movies> { content() } else cvComposable<Route.TvShows> { content() }
            }

            cvComposable<Route.Discover> {
                val model = cvViewModel("discover") { DiscoverViewModel(app) }
                DiscoverScreen(
                    viewModel = model,
                    onOpen = open,
                    onBrowse = { navController.navigate(it) },
                    onSurprise = {
                        scope.launch {
                            model.surprise()?.let(open)
                        }
                    },
                    modifier = Modifier.padding(top = padding.calculateTopPadding()),
                    shakeToPick = settings.shakeToPick,
                )
            }

            cvComposable<Route.MyList> {
                MyListScreen(
                    viewModel = cvViewModel("mylist") { MyListViewModel(app) },
                    onOpen = open,
                    onSignIn = { navController.navigate(Route.Auth) },
                    modifier = Modifier.padding(top = padding.calculateTopPadding()),
                    shakeToPick = settings.shakeToPick,
                )
            }

            cvComposable<Route.Stats> {
                StatsScreen(
                    viewModel = cvViewModel("stats") { StatsViewModel(app) },
                    onSignIn = { navController.navigate(Route.Auth) },
                    onYear = { navController.navigate(Route.YourYear()) },
                    onDiary = { navController.navigate(Route.Diary) },
                    onBadges = { navController.navigate(Route.Badges) },
                    onCollection = { navController.navigate(Route.Collection(it)) },
                    onFranchises = { navController.navigate(Route.Franchises) },
                    onPerson = { navController.navigate(Route.Person(it)) },
                    onOpen = open,
                    onTitleCollapse = { statsTitle = it },
                    modifier = Modifier.padding(top = padding.calculateTopPadding()),
                )
            }

            cvComposable<Route.Profile> {
                ProfileScreen(
                    viewModel = cvViewModel("profile") { ProfileViewModel(app) },
                    onSignIn = { navController.navigate(Route.Auth) },
                    onOpenUpdate = {
                        showUpdate = true
                        scope.launch { app.updates.check(force = true) }
                    },
                    onOpenReleaseNotes = { showHistory = true },
                    onOpenSettings = { navController.navigate(Route.Settings) },
                    modifier = Modifier.padding(top = padding.calculateTopPadding()),
                )
            }

            cvComposable<Route.Settings> {
                SettingsScreen(
                    // The SAME view model instance the profile tab uses, keyed
                    // by name rather than by back-stack entry: two instances
                    // would mean two DataStore collectors and a toggle that
                    // looks stale on whichever screen it was not changed from.
                    viewModel = cvViewModel("profile") { ProfileViewModel(app) },
                    onBack = { navController.popBackStack() },
                )
            }

            // Search, and the same search listening from the start. A sentence
            // can ask to go somewhere or play something, so it is given the way.
            val searchScreen: @Composable (Boolean) -> Unit = { listen ->
                SearchScreen(
                    viewModel = cvViewModel("search") { SearchViewModel(app) },
                    onOpen = open,
                    onBack = { navController.popBackStack() },
                    onPerson = { navController.navigate(Route.Person(it)) },
                    onTrailer = { key, title -> navController.navigate(Route.Trailer(key, title)) },
                    onNavigate = { page ->
                        // A tab is a place, not a step. Search closes first, or
                        // switching tab saves it into Home's stack and it comes
                        // back the next time Home is opened. Then, once the
                        // close has landed, the tab is reached exactly as the
                        // bar reaches it, so the saved tab states stay the
                        // bar's own: doing both in one frame left a blank page,
                        // and a home-made navigation left the Home tab dead.
                        fun tab(tab: Tab) {
                            navController.popBackStack()
                            scope.launch {
                                androidx.compose.runtime.withFrameNanos { }
                                androidx.compose.runtime.withFrameNanos { }
                                goToTab(tab)
                            }
                        }
                        when (page) {
                            "home" -> tab(Tab.Home)
                            "movies" -> tab(Tab.Movies)
                            "tv" -> tab(Tab.TvShows)
                            "list" -> tab(Tab.MyList)
                            "stats" -> tab(Tab.Stats)
                            "discover" -> tab(Tab.Discover)
                            "profile" -> tab(Tab.Profile)
                            "inbox" -> navController.navigate(Route.Inbox)
                            "settings" -> navController.navigate(Route.Settings)
                            "franchises" -> navController.navigate(Route.Franchises)
                            "box-office" -> navController.navigate(Route.BoxOffice)
                            "year" -> navController.navigate(Route.YourYear())
                            "top10" -> navController.navigate(Route.TopTen("movie"))
                        }
                    },
                    startListening = listen,
                )
            }
            cvComposable<Route.Search> { searchScreen(false) }
            cvComposable<Route.VoiceSearch> { searchScreen(true) }

            cvComposable<Route.Auth> {
                AuthScreen(
                    app = app,
                    onDone = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }

            cvComposable<Route.Detail> { entry ->
                val route: Route.Detail = entry.toRoute()
                val settings by app.settings.settings.collectAsStateWithLifecycle()
                DetailScreen(
                    viewModel = cvViewModel("detail_${route.type}_${route.id}") {
                        DetailViewModel(app, route.id, MediaType.of(route.type))
                    },
                    spoilerShield = settings.spoilerShield,
                    onBack = { navController.popBackStack() },
                    onOpen = open,
                    onPerson = { navController.navigate(Route.Person(it.id)) },
                    onPlayTrailer = { key, title ->
                        navController.navigate(Route.Trailer(key, title))
                    },
                    onShare = { detail ->
                        com.cineverse.app.feature.detail.shareTitle(navController.context, detail)
                    },
                    onCollection = { navController.navigate(Route.Collection(it)) },
                    onBrand = { brand -> navController.navigate(Route.Studio(brand.id, brand.isNetwork, brand.name)) },
                    onService = { id, name, logo -> navController.navigate(Route.Provider(id, name, logo)) },
                )
            }

            cvComposable<Route.Diary> {
                com.cineverse.app.feature.diary.DiaryScreen(
                    viewModel = cvViewModel("diary") { com.cineverse.app.feature.diary.DiaryViewModel(app) },
                    onOpen = open,
                    onBack = { navController.popBackStack() },
                )
            }

            cvComposable<Route.Badges> {
                com.cineverse.app.feature.badges.BadgesScreen(
                    viewModel = cvViewModel("badges") { com.cineverse.app.feature.badges.BadgesViewModel(app) },
                    onBack = { navController.popBackStack() },
                )
            }

            cvComposable<Route.Provider> { entry ->
                val route: Route.Provider = entry.toRoute()
                com.cineverse.app.feature.provider.ProviderScreen(
                    viewModel = cvViewModel("provider_${route.id}") {
                        com.cineverse.app.feature.provider.ProviderViewModel(app, route.id, route.name, route.logo)
                    },
                    onOpen = open,
                    onPeek = peek::open,
                    onBack = { navController.popBackStack() },
                )
            }

            cvComposable<Route.TopTen> { entry ->
                val route: Route.TopTen = entry.toRoute()
                com.cineverse.app.feature.topten.TopTenScreen(
                    viewModel = cvViewModel("topten") {
                        com.cineverse.app.feature.topten.TopTenViewModel(app, MediaType.of(route.type))
                    },
                    onOpen = open,
                    onBack = { navController.popBackStack() },
                )
            }

            cvComposable<Route.Inbox> {
                com.cineverse.app.feature.inbox.InboxScreen(
                    app = app,
                    onOpen = open,
                    onYear = { navController.navigate(Route.YourYear()) },
                    onBack = { navController.popBackStack() },
                )
            }

            cvComposable<Route.Studio> { entry ->
                val route: Route.Studio = entry.toRoute()
                com.cineverse.app.feature.studio.StudioScreen(
                    viewModel = cvViewModel("studio_${route.network}_${route.id}") {
                        com.cineverse.app.feature.studio.StudioViewModel(app, route)
                    },
                    title = route.name,
                    onOpen = open,
                    onBack = { navController.popBackStack() },
                )
            }

            cvComposable<Route.Trailer> { entry ->
                val route: Route.Trailer = entry.toRoute()
                TrailerScreen(
                    videoKey = route.key,
                    title = route.title,
                    onBack = { navController.popBackStack() },
                )
            }

            cvComposable<Route.Person> { entry ->
                val route: Route.Person = entry.toRoute()
                PersonScreen(
                    viewModel = cvViewModel("person_${route.id}") { PersonViewModel(app, route.id) },
                    onOpen = open,
                    onBack = { navController.popBackStack() },
                )
            }

            cvComposable<Route.Franchises> {
                com.cineverse.app.feature.franchise.FranchisesScreen(
                    viewModel = cvViewModel("franchises") {
                        com.cineverse.app.feature.franchise.FranchisesViewModel(app)
                    },
                    onOpen = open,
                    onCollection = { navController.navigate(Route.Collection(it)) },
                    onBack = { navController.popBackStack() },
                    onSignIn = { navController.navigate(Route.Auth) },
                )
            }

            cvComposable<Route.Collection> { entry ->
                val route: Route.Collection = entry.toRoute()
                com.cineverse.app.feature.franchise.CollectionScreen(
                    viewModel = cvViewModel("collection_${route.id}") {
                        com.cineverse.app.feature.franchise.CollectionViewModel(app, route.id)
                    },
                    onOpen = open,
                    onBack = { navController.popBackStack() },
                )
            }

            cvComposable<Route.BoxOffice> {
                com.cineverse.app.feature.boxoffice.BoxOfficeScreen(
                    viewModel = cvViewModel("boxoffice") {
                        com.cineverse.app.feature.boxoffice.BoxOfficeViewModel(app)
                    },
                    onOpen = open,
                    onCollection = { navController.navigate(Route.Collection(it)) },
                    onBack = { navController.popBackStack() },
                    onPerson = { navController.navigate(Route.Person(it)) },
                )
            }

            cvComposable<Route.YourYear> { entry ->
                val route: Route.YourYear = entry.toRoute()
                com.cineverse.app.feature.year.YourYearScreen(
                    viewModel = cvViewModel("year_${route.year}") {
                        com.cineverse.app.feature.year.YourYearViewModel(app, route.year)
                    },
                    onOpen = open,
                    onBack = { navController.popBackStack() },
                    onSignIn = { navController.navigate(Route.Auth) },
                )
            }

            cvComposable<Route.Browse> { entry ->
                val route: Route.Browse = entry.toRoute()
                BrowseScreen(
                    viewModel = cvViewModel("browse_${route.title}") { BrowseViewModel(app, route) },
                    title = route.title,
                    onOpen = open,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        }
        }
    }

    com.cineverse.app.feature.sheets.PeekHostSheet(
        app = app,
        host = peek,
        onOpen = { item -> navController.navigate(Route.Detail(item.id, item.type.wire)) },
        onRate = { item -> rateTarget = item },
    )

    rateTarget?.let { target ->
        val library by app.library.library.collectAsStateWithLifecycle()
        com.cineverse.app.feature.sheets.RatingSheet(
            title = target.title,
            current = library.ratingOf(target.key),
            celebrate = settings.confetti,
            onSave = { value ->
                scope.launch { app.library.setRating(target.key, value, target.title) }
            },
            onClear = { scope.launch { app.library.setRating(target.key, 0) } },
            onDismiss = { rateTarget = null },
        )
    }

    com.cineverse.app.feature.badges.CelebrationHost(
        app = app,
        onDiary = { navController.navigate(Route.Diary) },
        onBadges = { navController.navigate(Route.Badges) },
    )

    if (showUpdate) {
        UpdateSheet(
            state = update,
            currentVersion = app.updates.currentVersion,
            onDownload = { release -> scope.launch { app.updates.download(release) } },
            onInstall = { file -> app.updates.install(file) },
            onSkip = { release -> scope.launch { app.updates.skip(release); showUpdate = false } },
            onRetry = { scope.launch { app.updates.check(force = true) } },
            onHistory = { showUpdate = false; showHistory = true },
            onDismiss = { showUpdate = false; app.updates.dismiss() },
        )
    }

    // The order matters: what-is-new takes the screen over the history sheet,
    // because it is the one thing in the app that is allowed to interrupt.
    whatsNew?.let { release ->
        if (!showHistory) {
            WhatsNewSheet(
                release = release,
                onHistory = { app.updates.clearWhatsNew(); showHistory = true },
                onDismiss = { app.updates.clearWhatsNew() },
            )
        }
    }

    if (showHistory) {
        VersionHistorySheet(
            releases = history,
            currentCode = app.updates.currentCode,
            loading = loadingHistory,
            onDismiss = { showHistory = false },
        )
    }
}

// ---------- transitions ----------
//
// Pages are cards. Opening one slides it in from the right edge over the page
// you were on, which eases a quarter of the way left and dims behind it; going
// back slides the card off to the right and the page beneath glides back into
// place. It used to shrink away instead, which read as the page collapsing
// rather than being put down. Under a predictive back gesture the system
// scrubs this same slide with your thumb, so the card follows the finger.
// Switching between tabs is a plain crossfade: tabs are places side by side,
// not a stack.

private val CardEasing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val CARD_MS = 380

private fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs() =
    initialState.destination.isTab() && targetState.destination.isTab()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushEnter() =
    if (betweenTabs()) fadeIn(tween(Motion.Normal))
    else slideInHorizontally(tween(CARD_MS, easing = CardEasing)) { it }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushExit() =
    if (betweenTabs()) fadeOut(tween(Motion.Quick))
    else slideOutHorizontally(tween(CARD_MS, easing = CardEasing)) { -it / 4 } +
        fadeOut(tween(CARD_MS), targetAlpha = 0.45f)

/** The page you are going back TO: a quarter off to the left and dimmed, gliding home. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter() =
    if (betweenTabs()) fadeIn(tween(Motion.Normal))
    else slideInHorizontally(tween(CARD_MS, easing = CardEasing)) { -it / 4 } +
        fadeIn(tween(CARD_MS), initialAlpha = 0.45f)

/** Going back: the card slides off the right edge, whole. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit() =
    if (betweenTabs()) fadeOut(tween(Motion.Quick))
    else slideOutHorizontally(tween(CARD_MS, easing = CardEasing)) { it }
