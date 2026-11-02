package io.github.janetyulin.grounded.ui.list

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.janetyulin.grounded.R
import io.github.janetyulin.grounded.domain.model.Severity
import io.github.janetyulin.grounded.testing.string
import io.github.janetyulin.grounded.ui.common.EarthquakeUiState
import io.github.janetyulin.grounded.ui.common.ExternalLinks
import io.github.janetyulin.grounded.ui.common.FeedContent
import io.github.janetyulin.grounded.ui.common.LoadErrorUiState
import io.github.janetyulin.grounded.ui.common.LocationUiState
import io.github.janetyulin.grounded.ui.theme.GroundedTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<ListUiEvent>()
    private val clicks = mutableListOf<String>()
    private val links = mutableListOf<String>()
    private var locationActions = 0

    private val quake = EarthquakeUiState(
        "ci1", "5 km NW of Ridgecrest, CA", 4.6, Severity.MODERATE, 35.7, -117.6, 8.2, 23, 187, null, true
    )

    private fun loaded(error: LoadErrorUiState? = null, distanceKm: Int? = 187) =
        FeedContent.Loaded(listOf(quake.copy(distanceKm = distanceKm)), updatedMinutesAgo = 12, refreshError = error)

    @Test
    fun rowShowsPlaceTimeAndDistanceAndOpensDetails() {
        setContent(ListUiState(content = loaded(), location = LocationUiState.AVAILABLE))

        composeRule.onNodeWithText("187 km away", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(quake.place).performClick()

        assertEquals(listOf("ci1"), clicks)
    }

    @Test
    fun failedRefresh_keepsTheListAndSaysHowOldItIs() {
        setContent(ListUiState(content = loaded(error = LoadErrorUiState.NETWORK)))

        composeRule.onNodeWithText(quake.place).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.error_network), substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.action_retry)).performClick()

        assertEquals(listOf<ListUiEvent>(ListUiEvent.Refresh), events)
    }

    @Test
    fun nothingCached_showsFullScreenError() {
        setContent(ListUiState(content = FeedContent.Error(LoadErrorUiState.SERVER)))

        composeRule.onNodeWithText(string(R.string.feed_error_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.error_server)).assertIsDisplayed()
    }

    @Test
    fun withoutLocation_hidesDistanceAndOffersToAllow() {
        setContent(ListUiState(content = loaded(distanceKm = null), location = LocationUiState.PERMISSION_REQUIRED))

        composeRule.onNodeWithText("km away", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.action_allow)).performClick()

        assertEquals(1, locationActions)
    }

    @Test
    fun sortChipDispatchesSelection() {
        setContent(ListUiState(content = loaded(), sortOptions = ListSortUiState.entries))

        composeRule.onNodeWithText(string(R.string.sort_nearest)).performClick()

        assertEquals(listOf<ListUiEvent>(ListUiEvent.SelectSort(ListSortUiState.NEAREST)), events)
    }

    @Test
    fun tsunamiChipOpensOfficialGuidance() {
        setContent(ListUiState(content = loaded()))

        composeRule.onNodeWithText(string(R.string.tsunami_chip)).performClick()

        assertEquals(listOf(ExternalLinks.TSUNAMI_INFO), links)
    }

    private fun setContent(uiState: ListUiState) {
        composeRule.setContent {
            GroundedTheme {
                ListScreen(
                    uiState = uiState,
                    onEvent = { events += it },
                    onEarthquakeClick = { clicks += it },
                    locationPermissionPermanentlyDenied = false,
                    onLocationAction = { locationActions++ },
                    onOpenLink = { links += it }
                )
            }
        }
    }
}
