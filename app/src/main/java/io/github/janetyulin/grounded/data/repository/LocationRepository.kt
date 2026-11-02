package io.github.janetyulin.grounded.data.repository

import androidx.datastore.core.DataStore
import io.github.janetyulin.grounded.data.location.LocationProvider
import io.github.janetyulin.grounded.data.model.LastLocation
import io.github.janetyulin.grounded.data.model.UserLocation
import io.github.janetyulin.grounded.domain.model.GeoPoint
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Single source of truth for the device's location.
 *
 * [location] is live and in memory. The most recent fix is also saved (coarse, on-device, excluded
 * from backup) as [lastSaved], because background alert checks run without location access. Losing
 * the permission clears it.
 */
interface LocationRepository {

    val location: StateFlow<UserLocation>

    /** Checks permission and asks for a fresh fix. Called when the app comes to the foreground and after a grant. */
    suspend fun refresh()

    /** The last fix seen while the app was open, or `null` if there never was one. */
    suspend fun lastSaved(): UserLocation.Available?
}

/**
 * Default implementation of [LocationRepository].
 */
@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val provider: LocationProvider,
    private val lastLocationStore: DataStore<LastLocation>
) : LocationRepository {

    private val _location = MutableStateFlow<UserLocation>(UserLocation.Unknown)
    override val location: StateFlow<UserLocation> = _location.asStateFlow()

    override suspend fun refresh() {
        if (!provider.hasPermission()) {
            _location.value = UserLocation.PermissionRequired
            // Revoking access also means "stop using where I was": background checks lose it too.
            lastLocationStore.updateData { LastLocation() }
            return
        }
        // Just granted: stop asking for the permission right away, while the fix (which can take
        // seconds) is on its way. Other states keep showing until the new result replaces them.
        if (_location.value == UserLocation.PermissionRequired) _location.value = UserLocation.Unknown
        val fresh = provider.currentLocation()
        _location.value = when {
            // A slightly old fix is more useful than none when this attempt couldn't get one.
            fresh is UserLocation.Unavailable && _location.value is UserLocation.Available -> _location.value

            else -> fresh
        }
        if (fresh is UserLocation.Available) {
            lastLocationStore.updateData {
                LastLocation(fresh.point.latitude, fresh.point.longitude, fresh.fixedAt.toEpochMilli())
            }
        }
    }

    override suspend fun lastSaved(): UserLocation.Available? {
        val saved = try {
            lastLocationStore.data.first()
        } catch (e: IOException) {
            return null
        }
        val latitude = saved.latitude ?: return null
        val longitude = saved.longitude ?: return null
        return UserLocation.Available(GeoPoint(latitude, longitude), Instant.ofEpochMilli(saved.fixedAtMillis))
    }
}
