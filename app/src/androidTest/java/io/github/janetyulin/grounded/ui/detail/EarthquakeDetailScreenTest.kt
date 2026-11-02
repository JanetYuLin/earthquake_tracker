package io.github.janetyulin.grounded.ui.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.janetyulin.grounded.R
import io.github.janetyulin.grounded.domain.model.Severity
import io.github.janetyulin.grounded.testing.string
import io.github.janetyulin.grounded.ui.common.ExternalLinks
import io.github.janetyulin.grounded.ui.common.LoadErrorUiState
import io.github.janetyulin.grounded.ui.theme.GroundedTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EarthquakeDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<EarthquakeDetailUiEvent>()
    private val links = mutableListOf<String>()

    private val details = EarthquakeDetailsUiState(
        title = "M 4.6 - Ridgecrest", place = "5 km NW of Ridgecrest, CA", magnitude = 4.6,
        severity = Severity.MODERATE, timeText = "Oct 31, 2026, 9:41 AM", distanceKm = 187, depthKm = 8.2,
        latitude = 35.7, longitude = -117.6, reviewed = true, feltReports = 120, alertLevel = null, tsunamiFlag = true,
        url = "https://earthquake.usgs.gov/earthquakes/eventpage/ci1"
    )

    @Test
    fun loaded_showsFieldsAndOpensUsgs() {
        setContent(EarthquakeDetailUiState.Loaded(details))

        composeRule.onNodeWithText(details.place).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.detail_depth_value, 8.2)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.detail_status_reviewed)).assertIsDisplayed()
        composeRule.onNodeWithText("120").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.detail_distance_value, 187)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.detail_open_usgs)).performScrollTo().performClick()

        assertEquals(listOf(details.url), links)
    }

    @Test
    fun error_retryDispatchesRetry() {
        setContent(EarthquakeDetailUiState.Error(LoadErrorUiState.NETWORK))

        composeRule.onNodeWithText(string(R.string.error_network)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.action_retry)).performClick()

        assertEquals(listOf<EarthquakeDetailUiEvent>(EarthquakeDetailUiEvent.Retry), events)
    }

    @Test
    fun safetySection_expandsAndLinksToOfficialGuidance() {
        setContent(EarthquakeDetailUiState.Loaded(details))

        composeRule.onNodeWithText(string(R.string.safety_title)).performScrollTo().performClick()
        composeRule.onNodeWithText(string(R.string.safety_link_tsunami)).performScrollTo().performClick()

        assertEquals(listOf(ExternalLinks.TSUNAMI_INFO), links)
    }

    private fun setContent(uiState: EarthquakeDetailUiState) {
        composeRule.setContent {
            GroundedTheme {
                EarthquakeDetailScreen(
                    uiState = uiState,
                    onEvent = { events += it },
                    onOpenLink = { links += it }
                )
            }
        }
    }
}
