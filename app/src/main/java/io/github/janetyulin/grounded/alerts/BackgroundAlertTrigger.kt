package io.github.janetyulin.grounded.alerts

import io.github.janetyulin.grounded.core.logging.LoggingManager
import io.github.janetyulin.grounded.data.repository.EarthquakeRepository
import io.github.janetyulin.grounded.data.repository.LocationRepository
import io.github.janetyulin.grounded.data.repository.UserPreferencesRepository
import io.github.janetyulin.grounded.domain.model.TimePeriod
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * One background alert check, run periodically by [NearbyAlertWorker]: refreshes the past-day feed
 * (which also keeps the offline cache warm) and checks it against the last location seen while the
 * app was open, since a background process can't ask for a fresh one.
 */
class BackgroundAlertTrigger @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val earthquakeRepository: EarthquakeRepository,
    private val locationRepository: LocationRepository,
    private val alertCheck: NearbyAlertCheck,
    private val logger: LoggingManager
) {
    suspend fun run() {
        if (!preferencesRepository.preferences.first().alerts.enabled) return
        // Every alertable event is from the last few hours, so the day feed is enough whatever the
        // screens show. A failed download still checks the cache; it simply finds nothing new.
        earthquakeRepository.refresh(TimePeriod.DAY)
        val feed = earthquakeRepository.feed(TimePeriod.DAY).first().feed ?: return
        val here = locationRepository.lastSaved()?.point
        if (here == null) logger.info(TAG, "No saved location; recording without alerting")
        alertCheck.check(feed.earthquakes, here)
    }

    private companion object {
        const val TAG = "BackgroundAlertTrigger"
    }
}
