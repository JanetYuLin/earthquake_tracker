package io.github.janetyulin.grounded.ui.alerts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.janetyulin.grounded.R
import io.github.janetyulin.grounded.testing.string
import io.github.janetyulin.grounded.ui.common.LocationUiState
import io.github.janetyulin.grounded.ui.theme.GroundedTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlertsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<AlertsUiEvent>()
    private val toggles = mutableListOf<Boolean>()

    @Test
    fun toggleIsHandedToTheRoute() {
        setContent(AlertsUiState(isLoading = false))

        composeRule.onNodeWithText(string(R.string.alerts_toggle)).performClick()

        assertEquals(listOf(true), toggles)
    }

    @Test
    fun magnitudeChipDispatchesEvent() {
        setContent(AlertsUiState(isLoading = false))

        composeRule.onNodeWithText(string(R.string.alerts_magnitude_option, 4.5)).performClick()

        assertEquals(listOf<AlertsUiEvent>(AlertsUiEvent.SetMinMagnitude(4.5)), events)
    }

    @Test
    fun enabledWithoutLocationOrNotifications_explainsBoth() {
        setContent(
            AlertsUiState(
                isLoading = false,
                enabled = true,
                notificationPermissionDenied = true,
                location = LocationUiState.PERMISSION_REQUIRED
            )
        )

        composeRule.onNodeWithText(string(R.string.alerts_permission_denied)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.location_permission_message)).assertIsDisplayed()
    }

    @Test
    fun keepsTheNotAnEarlyWarningDisclaimer() {
        setContent(AlertsUiState(isLoading = false))

        composeRule.onNodeWithText(string(R.string.alerts_disclaimer)).assertIsDisplayed()
    }

    private fun setContent(uiState: AlertsUiState) {
        composeRule.setContent {
            GroundedTheme {
                AlertsScreen(
                    uiState = uiState,
                    onEvent = { events += it },
                    onAlertsToggled = { toggles += it },
                    locationPermissionPermanentlyDenied = false,
                    onLocationAction = {}
                )
            }
        }
    }
}
