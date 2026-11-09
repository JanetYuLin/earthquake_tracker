package io.github.janetyulin.grounded.alerts

/**
 * A nearby-earthquake notification, independent of how it is displayed.
 */
data class EarthquakeAlert(val earthquakeId: String, val magnitude: Double, val place: String, val distanceKm: Int)
