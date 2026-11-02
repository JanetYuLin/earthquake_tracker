package io.github.janetyulin.grounded.data.model

import io.github.janetyulin.grounded.domain.model.AlertRules
import io.github.janetyulin.grounded.domain.model.TimePeriod
import kotlinx.serialization.Serializable

/**
 * Everything the user chooses in the app. Persisted on the device as one JSON document.
 */
@Serializable
data class UserPreferences(
    val alerts: AlertPreferences = AlertPreferences(),
    /** Shared by Map and List so both always show the same earthquakes. */
    val selectedPeriod: TimePeriod = TimePeriod.DAY
)

@Serializable
data class AlertPreferences(
    val enabled: Boolean = false,
    val minMagnitude: Double = AlertRules.DEFAULT_MIN_MAGNITUDE,
    val radiusKm: Int = AlertRules.DEFAULT_RADIUS_KM
)
