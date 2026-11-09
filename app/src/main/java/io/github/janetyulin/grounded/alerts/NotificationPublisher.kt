package io.github.janetyulin.grounded.alerts

/**
 * Posts user-visible notifications.
 */
interface NotificationPublisher {
    /** Registers notification channels; safe to call repeatedly. */
    fun createChannels()

    fun showEarthquakeAlert(alert: EarthquakeAlert)
}
