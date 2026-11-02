package io.github.janetyulin.grounded.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.janetyulin.grounded.R
import io.github.janetyulin.grounded.domain.model.GeoPoint
import io.github.janetyulin.grounded.domain.model.Severity
import io.github.janetyulin.grounded.domain.model.TimePeriod
import io.github.janetyulin.grounded.testing.plural
import io.github.janetyulin.grounded.testing.string
import io.github.janetyulin.grounded.ui.common.EarthquakeUiState
import io.github.janetyulin.grounded.ui.common.FeedContent
import io.github.janetyulin.grounded.ui.common.LoadErrorUiState
import io.github.janetyulin.grounded.ui.common.LocationUiState
import io.github.janetyulin.grounded.ui.theme.GroundedTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<MapUiEvent>()
    private val detailsClicks = mutableListOf<String>()
    private var locationActions = 0
    private var mapReceived: Pair<List<EarthquakeUiState>, GeoPoint?>? = null

    private val quake = EarthquakeUiState(
        "ci1", "5 km NW of Ridgecrest, CA", 5.4, Severity.STRONG, 35.7, -117.6, 8.2, 30, 187, null, false
    )
    private val here = GeoPoint(34.15, -118.14)

    private fun loaded(vararg quakes: EarthquakeUiState, error: LoadErrorUiState? = null) =
        FeedContent.Loaded(quakes.toList(), updatedMinutesAgo = 5, refreshError = error)

    @Test
    fun loaded_passesEarthquakesAndUserToMapAndShowsCount() {
        setContent(MapUiState(content = loaded(quake), userLocation = here, location = LocationUiState.AVAILABLE))

        composeRule.onNodeWithTag("map").assertIsDisplayed()
        composeRule.onNodeWithText(plural(R.plurals.map_count, 1, 1)).assertIsDisplayed()
        assertEquals(listOf(quake) to here, mapReceived)
    }

    @Test
    fun periodChipDispatchesSelection() {
        setContent(MapUiState(content = loaded()))

        composeRule.onNodeWithText(string(R.string.period_week)).performClick()

        assertEquals(listOf<MapUiEvent>(MapUiEvent.SelectPeriod(TimePeriod.WEEK)), events)
    }

    @Test
    fun refreshButtonDispatchesRefresh() {
        setContent(MapUiState(content = loaded()))

        composeRule.onNodeWithContentDescription(string(R.string.action_refresh)).performClick()

        assertEquals(listOf<MapUiEvent>(MapUiEvent.Refresh), events)
    }

    @Test
    fun failedRefresh_keepsMapAndOffersRetry() {
        setContent(MapUiState(content = loaded(quake, error = LoadErrorUiState.NETWORK)))

        composeRule.onNodeWithTag("map").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.error_network), substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.action_retry)).performClick()

        assertEquals(listOf<MapUiEvent>(MapUiEvent.Refresh), events)
    }

    @Test
    fun missingPermission_showsLocationBannerAction() {
        setContent(MapUiState(content = loaded(), location = LocationUiState.PERMISSION_REQUIRED))

        composeRule.onNodeWithText(string(R.string.location_permission_message)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.action_allow)).performClick()

        assertEquals(1, locationActions)
    }

    @Test
    fun mapPointsScreenReadersToTheList() {
        setContent(MapUiState(content = loaded(quake)))

        composeRule.onNodeWithContentDescription(plural(R.plurals.map_description, 1, 1)).assertExists()
    }

    @Test
    fun selectedEarthquakeSheetShowsDistanceAndOpensDetails() {
        setContent(MapUiState(content = loaded(quake), selected = quake))

        composeRule.onNodeWithText(quake.place).assertIsDisplayed()
        composeRule.onNodeWithText("187 km", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.map_details)).performClick()

        assertEquals(listOf("ci1"), detailsClicks)
        assertEquals(listOf<MapUiEvent>(MapUiEvent.DismissSelection), events)
    }

    private fun setContent(uiState: MapUiState) {
        composeRule.setContent {
            GroundedTheme {
                MapScreen(
                    uiState = uiState,
                    onEvent = { events += it },
                    onEarthquakeDetailsClick = { detailsClicks += it },
                    locationPermissionPermanentlyDenied = false,
                    onLocationAction = { locationActions++ },
                    map = { earthquakes, userLocation, _, modifier ->
                        mapReceived = earthquakes to userLocation
                        Box(modifier.testTag("map"))
                    }
                )
            }
        }
    }
}
