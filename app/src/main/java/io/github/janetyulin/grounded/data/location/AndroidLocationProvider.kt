package io.github.janetyulin.grounded.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.janetyulin.grounded.data.model.UserLocation
import io.github.janetyulin.grounded.domain.model.GeoPoint
import java.time.Clock
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [LocationProvider] backed by the platform [LocationManager] with coarse (city-level) accuracy,
 * which is enough for distances in kilometres and needs no Google Play services.
 */
class AndroidLocationProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock
) : LocationProvider {

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun currentLocation(): UserLocation {
        // Checked inline (not via hasPermission) so lint can see the guard before the location calls.
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return UserLocation.PermissionRequired

        val manager = context.getSystemService(LocationManager::class.java)
        if (manager == null || !manager.isLocationEnabled) return UserLocation.Unavailable
        val provider = if (manager.hasProvider(LocationManager.FUSED_PROVIDER)) {
            LocationManager.FUSED_PROVIDER
        } else {
            LocationManager.NETWORK_PROVIDER
        }

        val location = try {
            withTimeoutOrNull(FIX_TIMEOUT) { manager.awaitCurrentLocation(provider) }
                ?: manager.getLastKnownLocation(provider)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the request.
            return UserLocation.PermissionRequired
        } catch (e: IllegalArgumentException) {
            // The provider doesn't exist on this device.
            return UserLocation.Unavailable
        }
        return location
            ?.let { UserLocation.Available(GeoPoint(it.latitude, it.longitude), clock.instant()) }
            ?: UserLocation.Unavailable
    }

    @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    private suspend fun LocationManager.awaitCurrentLocation(provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            getCurrentLocation(provider, signal, context.mainExecutor) { location ->
                if (continuation.isActive) continuation.resume(location)
            }
        }

    private companion object {
        val FIX_TIMEOUT = 10.seconds
    }
}
