package io.github.janetyulin.grounded.alerts

import io.github.janetyulin.grounded.data.model.AlertPrimingKey

/**
 * What one alert check should do: which ids to record as handled, and which alerts to post.
 */
internal data class AlertDecision(
    val primedFor: AlertPrimingKey,
    val record: List<String>,
    val notify: List<EarthquakeAlert>
)
