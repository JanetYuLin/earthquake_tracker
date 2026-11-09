package io.github.janetyulin.grounded.alerts

import io.github.janetyulin.grounded.core.work.ForegroundRepeater
import io.github.janetyulin.grounded.data.repository.EarthquakeRepository
import io.github.janetyulin.grounded.data.repository.LocationRepository
import io.github.janetyulin.grounded.data.repository.UserPreferencesRepository
import io.github.janetyulin.grounded.di.ApplicationScope
import io.github.janetyulin.grounded.di.PollInterval
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the device location and the selected earthquake feed fresh **only while the app is in the
 * foreground**: right away when the app comes to the foreground or the selected period changes, then
 * every [interval]. The timing comes from [ForegroundRepeater]; this class only decides what to refresh.
 */
@Singleton
class NearbyEarthquakePoller @Inject constructor(
    private val repeater: ForegroundRepeater,
    private val preferencesRepository: UserPreferencesRepository,
    private val earthquakeRepository: EarthquakeRepository,
    private val locationRepository: LocationRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
    @param:PollInterval private val interval: Duration
) {
    private var started = false

    /** Starts polling for the lifetime of [scope]. Idempotent; call from the main thread. */
    fun start() {
        if (started) return
        started = true
        scope.launch { run() }
    }

    internal suspend fun run() {
        val period = preferencesRepository.preferences.map { it.selectedPeriod }
        repeater.repeatWhileForeground(TAG, period, interval) { current ->
            // Location first, so the checker sees the fresh position with the fresh feed.
            locationRepository.refresh()
            earthquakeRepository.refresh(current)
        }
    }

    private companion object {
        const val TAG = "NearbyPoller"
    }
}
