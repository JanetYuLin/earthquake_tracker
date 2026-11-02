package io.github.janetyulin.grounded.alerts

/**
 * A nearby-earthquake notification, independent of how it is displayed.
 */
data class EarthquakeAlert(val earthquakeId: String, val magnitude: Double, val place: String, val distanceKm: Int)

/**
 * Posts user-visible notifications.
 */
interface NotificationPublisher {
    /** Registers notification channels; safe to call repeatedly. */
    fun createChannels()

    fun showEarthquakeAlert(alert: EarthquakeAlert)
}
