package io.github.janetyulin.grounded.ui.alerts

import io.github.janetyulin.grounded.domain.model.AlertRules
import io.github.janetyulin.grounded.ui.common.LocationUiState

/**
 * Single UI State for the Alerts screen.
 *
 * @param notificationPermissionDenied Alerts are on but the system blocks notifications.
 */
data class AlertsUiState(
    val isLoading: Boolean = true,
    val enabled: Boolean = false,
    val minMagnitude: Double = AlertRules.DEFAULT_MIN_MAGNITUDE,
    val radiusKm: Int = AlertRules.DEFAULT_RADIUS_KM,
    val notificationPermissionDenied: Boolean = false,
    /** Alerts are measured from the device, so they need its location. */
    val location: LocationUiState = LocationUiState.CHECKING
)

sealed interface AlertsUiEvent {
    data class SetAlertsEnabled(val enabled: Boolean) : AlertsUiEvent

    /** Current notification permission, reported by the route on every resume and after a request. */
    data class NotificationPermissionChecked(val granted: Boolean) : AlertsUiEvent
    data class SetMinMagnitude(val magnitude: Double) : AlertsUiEvent
    data class SetRadius(val radiusKm: Int) : AlertsUiEvent
    data object CheckLocation : AlertsUiEvent
}
