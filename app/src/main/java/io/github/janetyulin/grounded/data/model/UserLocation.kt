package io.github.janetyulin.grounded.data.model

import io.github.janetyulin.grounded.domain.model.GeoPoint
import java.time.Instant

/**
 * Where the device is, as far as the app knows.
 */
sealed interface UserLocation {
    /** Not checked yet. */
    data object Unknown : UserLocation

    /** The user hasn't granted location access. */
    data object PermissionRequired : UserLocation

    /** Access is granted, but no fix is available (location turned off, no provider, timeout). */
    data object Unavailable : UserLocation

    data class Available(val point: GeoPoint, val fixedAt: Instant) : UserLocation
}

/** The device position if known, otherwise `null`. */
val UserLocation.pointOrNull: GeoPoint?
    get() = (this as? UserLocation.Available)?.point
