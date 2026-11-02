package io.github.janetyulin.grounded.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.janetyulin.grounded.R
import io.github.janetyulin.grounded.ui.alerts.AlertsRoute
import io.github.janetyulin.grounded.ui.detail.EarthquakeDetailRoute
import io.github.janetyulin.grounded.ui.list.ListRoute
import io.github.janetyulin.grounded.ui.map.MapRoute
import io.github.janetyulin.grounded.ui.map.rememberQuakeMapHolder

/**
 * Main application Navigation Host extracting navigation and UI orchestration out of MainActivity.
 * Owns the app chrome: a global top app bar (title per destination, back arrow inside a tab) and the
 * bottom navigation bar, so individual screens never draw their own.
 *
 * @param onScreenViewed Invoked each time a different destination becomes the top of the back stack.
 * @param onExit Invoked when Back is pressed on the start tab's root; the host should finish.
 * @param deepLinkEarthquakeId Earthquake to open (e.g. from a notification); [onDeepLinkHandled] is
 * called once it has been pushed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroundedNavHost(
    onScreenViewed: (AppNavKey) -> Unit,
    onExit: () -> Unit,
    deepLinkEarthquakeId: String?,
    onDeepLinkHandled: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topLevelBackStack = rememberSaveable(saver = TopLevelBackStack.Saver) { TopLevelBackStack(RouteMap) }

    val currentKey = topLevelBackStack.backStack.last()
    // The effects below outlive a recomposition, so read the latest callbacks rather than the first ones.
    val currentOnScreenViewed by rememberUpdatedState(onScreenViewed)
    val currentOnDeepLinkHandled by rememberUpdatedState(onDeepLinkHandled)
    // Saved so that recreating the activity (e.g. rotation) doesn't count a second view of the same screen.
    var lastTrackedKey by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(currentKey) {
        val keyId = currentKey.toString()
        if (keyId != lastTrackedKey) {
            lastTrackedKey = keyId
            currentOnScreenViewed(currentKey)
        }
    }

    val openEarthquake: (String) -> Unit = { id -> topLevelBackStack.add(RouteEarthquakeDetail(id)) }
    // One native map for the whole session: the Map entry leaves composition on every tab switch,
    // so the view is held here and only shown or hidden.
    val mapHolder = rememberQuakeMapHolder()

    LaunchedEffect(deepLinkEarthquakeId) {
        val id = deepLinkEarthquakeId ?: return@LaunchedEffect
        // Alerts are about nearby events, so open them from the List tab, where the distance is front and center.
        topLevelBackStack.addTopLevel(RouteList)
        openEarthquake(id)
        currentOnDeepLinkHandled()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(currentKey.titleResId)) },
                navigationIcon = {
                    if (topLevelBackStack.canGoBackWithinTab) {
                        IconButton(onClick = { topLevelBackStack.popWithinTab() }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                TOP_LEVEL_NAV_KEYS.forEach { navKey ->
                    val label = stringResource(navKey.labelResId)
                    NavigationBarItem(
                        selected = navKey == topLevelBackStack.topLevelKey,
                        onClick = { topLevelBackStack.addTopLevel(navKey) },
                        icon = {
                            Icon(
                                painter = painterResource(id = navKey.iconResId),
                                contentDescription = null
                            )
                        },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavDisplay(
            backStack = topLevelBackStack.backStack,
            onBack = { if (!topLevelBackStack.removeLast()) onExit() },
            // Consume the bars' insets so nested Scaffolds/sheets don't apply them a second time.
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            // Scope saved state and ViewModels to each NavEntry; cleared when the entry leaves the back stack.
            // Tab switches swap instantly (Material allows this for top-level destinations). The map
            // is a SurfaceView, which Compose can't fade, so a crossfade left it lingering on top.
            transitionSpec = { NoTransition },
            popTransitionSpec = { NoTransition },
            predictivePopTransitionSpec = { NoTransition },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            ),
            entryProvider = { key ->
                when (key) {
                    is RouteMap -> NavEntry(key) {
                        MapRoute(mapHolder = mapHolder, onEarthquakeDetailsClick = openEarthquake)
                    }

                    is RouteList -> NavEntry(key) {
                        ListRoute(onEarthquakeClick = openEarthquake)
                    }

                    is RouteAlerts -> NavEntry(key) {
                        AlertsRoute()
                    }

                    // Details slide over the current tab. The background makes them opaque during the
                    // slide; otherwise the tab below shows through until its entry is removed.
                    is RouteEarthquakeDetail -> NavEntry(key, metadata = DetailTransitions) {
                        EarthquakeDetailRoute(
                            earthquakeId = key.earthquakeId,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        )
                    }
                }
            }
        )
    }
}

private val NoTransition: ContentTransform = EnterTransition.None togetherWith ExitTransition.None

private val DetailTransitions: Map<String, Any> =
    NavDisplay.transitionSpec {
        slideInHorizontally(initialOffsetX = { it }) togetherWith ExitTransition.KeepUntilTransitionsFinished
    } + NavDisplay.popTransitionSpec {
        EnterTransition.None togetherWith slideOutHorizontally(targetOffsetX = { it })
    } + NavDisplay.predictivePopTransitionSpec {
        EnterTransition.None togetherWith slideOutHorizontally(targetOffsetX = { it })
    }
