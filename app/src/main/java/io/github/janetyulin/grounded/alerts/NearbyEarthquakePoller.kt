package io.github.janetyulin.grounded.alerts

import io.github.janetyulin.grounded.core.lifecycle.AppForegroundMonitor
import io.github.janetyulin.grounded.core.logging.LoggingManager
import io.github.janetyulin.grounded.data.repository.EarthquakeRepository
import io.github.janetyulin.grounded.data.repository.LocationRepository
import io.github.janetyulin.grounded.data.repository.UserPreferencesRepository
import io.github.janetyulin.grounded.di.ApplicationScope
import io.github.janetyulin.grounded.di.PollInterval
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the device location and the selected earthquake feed fresh **only while the app is in the
 * foreground**.
 *
 * Refreshes immediately when the app comes to the foreground (or the selected period changes), then
 * every [interval]. Going to the background cancels the loop, so no requests or timers run.
 */
@Singleton
class NearbyEarthquakePoller @Inject constructor(
    private val foregroundMonitor: AppForegroundMonitor,
    private val preferencesRepository: UserPreferencesRepository,
    private val earthquakeRepository: EarthquakeRepository,
    private val locationRepository: LocationRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
    @param:PollInterval private val interval: Duration,
    private val logger: LoggingManager
) {
    private var started = false

    /** Starts polling for the lifetime of [scope]. Idempotent; call from the main thread. */
    fun start() {
        if (started) return
        started = true
        scope.launch { run() }
    }

    internal suspend fun run() {
        val period = preferencesRepository.preferences.map { it.selectedPeriod }.distinctUntilChanged()

        combine(foregroundMonitor.isForeground, period) { foreground, current -> current.takeIf { foreground } }
            .distinctUntilChanged()
            .collectLatest { current ->
                if (current == null) {
                    logger.info(TAG, "Polling paused")
                    return@collectLatest
                }
                logger.info(TAG, "Polling every ${interval.toMinutes()} min")
                while (true) {
                    // Location first, so the notifier sees the fresh position with the fresh feed.
                    locationRepository.refresh()
                    earthquakeRepository.refresh(current)
                    delay(interval.toMillis().milliseconds)
                }
            }
    }

    private companion object {
        const val TAG = "NearbyPoller"
    }
}
