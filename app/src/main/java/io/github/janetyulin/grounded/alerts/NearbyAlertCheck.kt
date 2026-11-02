package io.github.janetyulin.grounded.alerts

import io.github.janetyulin.grounded.core.logging.LoggingManager
import io.github.janetyulin.grounded.data.model.Earthquake
import io.github.janetyulin.grounded.data.repository.AlertStateRepository
import io.github.janetyulin.grounded.data.repository.UserPreferencesRepository
import io.github.janetyulin.grounded.domain.model.GeoPoint
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Applies [decideAlerts] to a set of earthquakes and posts the result. Used by the foreground
 * [ForegroundAlertTrigger] and the background [BackgroundAlertTrigger]; both run in this process, and the
 * lock makes sure they never both post the same event.
 */
@Singleton
class NearbyAlertCheck @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val alertStateRepository: AlertStateRepository,
    private val publisher: NotificationPublisher,
    private val clock: Clock,
    private val logger: LoggingManager
) {
    private val lock = Mutex()

    suspend fun check(quakes: List<Earthquake>, here: GeoPoint?) = lock.withLock {
        val alerts = preferencesRepository.preferences.first().alerts
        val state = alertStateRepository.current()
        val decision = decideAlerts(quakes, here, alerts, state, clock.instant())
        // Record before publishing so a failure while posting can never re-notify in a loop.
        if (decision.record.isNotEmpty() || decision.primedFor != state.primedFor) {
            alertStateRepository.record(decision.primedFor, decision.record)
        }
        decision.notify.forEach { alert ->
            publisher.showEarthquakeAlert(alert)
            logger.info(TAG, "Notified ${alert.earthquakeId}")
        }
    }

    private companion object {
        const val TAG = "NearbyAlertCheck"
    }
}
