package io.github.janetyulin.grounded.data.location

import io.github.janetyulin.grounded.data.model.UserLocation

/**
 * Asks the platform for the device's current position. Main-safe.
 */
interface LocationProvider {
    /** Whether the app may ask for a position. Instant, unlike [currentLocation]. */
    fun hasPermission(): Boolean

    /** Waits for a fix, which can take seconds. Never returns [UserLocation.Unknown]. */
    suspend fun currentLocation(): UserLocation
}
