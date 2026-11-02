package io.github.janetyulin.grounded.alerts

import io.github.janetyulin.grounded.data.model.pointOrNull
import io.github.janetyulin.grounded.data.repository.EarthquakeRepository
import io.github.janetyulin.grounded.data.repository.LocationRepository
import io.github.janetyulin.grounded.data.repository.UserPreferencesRepository
import io.github.janetyulin.grounded.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/**
 * Foreground alerts: runs [NearbyAlertCheck] whenever the cached feed or the live location changes,
 * so the check observes the same state as the Map and List screens and never fetches on its own.
 * Background checks are [BackgroundAlertTrigger]'s job.
 */
@Singleton
class ForegroundAlertTrigger @Inject constructor(
    private val earthquakeRepository: EarthquakeRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val locationRepository: LocationRepository,
    private val alertCheck: NearbyAlertCheck,
    private val publisher: NotificationPublisher,
    @param:ApplicationScope private val scope: CoroutineScope
) {
    private var started = false

    /** Starts observing for the lifetime of [scope]. Idempotent; call from the main thread. */
    fun start() {
        if (started) return
        started = true
        publisher.createChannels()
        scope.launch { run() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    internal suspend fun run() {
        val feed = preferencesRepository.preferences
            .map { it.selectedPeriod }
            .distinctUntilChanged()
            .flatMapLatest { earthquakeRepository.feed(it) }
            .mapNotNull { it.feed }
            .distinctUntilChanged()

        combine(feed, locationRepository.location) { current, location -> current to location }
            .collect { (current, location) -> alertCheck.check(current.earthquakes, location.pointOrNull) }
    }
}
