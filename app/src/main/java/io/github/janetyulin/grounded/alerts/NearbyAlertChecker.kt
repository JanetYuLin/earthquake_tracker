package io.github.janetyulin.grounded.alerts

import io.github.janetyulin.grounded.core.logging.LoggingManager
import io.github.janetyulin.grounded.core.work.PeriodicWork
import io.github.janetyulin.grounded.core.work.PeriodicWorkScheduler
import io.github.janetyulin.grounded.core.work.keepScheduledWhile
import io.github.janetyulin.grounded.data.model.Earthquake
import io.github.janetyulin.grounded.data.model.pointOrNull
import io.github.janetyulin.grounded.data.repository.AlertStateRepository
import io.github.janetyulin.grounded.data.repository.EarthquakeRepository
import io.github.janetyulin.grounded.data.repository.LocationRepository
import io.github.janetyulin.grounded.data.repository.UserPreferencesRepository
import io.github.janetyulin.grounded.di.ApplicationScope
import io.github.janetyulin.grounded.domain.model.GeoPoint
import io.github.janetyulin.grounded.domain.model.TimePeriod
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Nearby-earthquake alerts. Two entry points share one check:
 *
 * - **Foreground** (started by [start]): checks whenever the cached feed or the live location changes,
 *   so it sees the same state as the Map and List screens and never fetches on its own.
 * - **Background** ([checkInBackground], registered as the [BACKGROUND_TASK] task): refreshes the past-day
 *   feed and checks it against the last location saved while the app was open. [start] keeps it
 *   scheduled only while alerts are on, so users without alerts get no background work.
 *
 * The check applies [decideAlerts], records handled ids and posts the alerts, under a lock so the two
 * paths never post the same event.
 */
@Singleton
class NearbyAlertChecker @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val earthquakeRepository: EarthquakeRepository,
    private val locationRepository: LocationRepository,
    private val alertStateRepository: AlertStateRepository,
    private val publisher: NotificationPublisher,
    private val workScheduler: PeriodicWorkScheduler,
    private val clock: Clock,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val logger: LoggingManager
) {
    private val lock = Mutex()
    private var started = false

    /**
     * Starts foreground checks and keeps background checks scheduled while alerts are on, for the
     * lifetime of [scope]. Idempotent; call from the main thread.
     */
    fun start() {
        if (started) return
        started = true
        publisher.createChannels()
        scope.launch { observeForeground() }
        scope.launch { scheduleBackgroundChecks() }
    }

    internal suspend fun scheduleBackgroundChecks() {
        workScheduler.keepScheduledWhile(preferencesRepository.preferences.map { it.alerts.enabled }, BACKGROUND_WORK)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    internal suspend fun observeForeground() {
        val feed = preferencesRepository.preferences
            .map { it.selectedPeriod }
            .distinctUntilChanged()
            .flatMapLatest { earthquakeRepository.feed(it) }
            .mapNotNull { it.feed }
            .distinctUntilChanged()

        combine(feed, locationRepository.location) { current, location -> current to location }
            .collect { (current, location) -> check(current.earthquakes, location.pointOrNull) }
    }

    /** One background check, run as the [BACKGROUND_TASK] task. Does nothing while alerts are off. */
    suspend fun checkInBackground() {
        if (!preferencesRepository.preferences.first().alerts.enabled) return
        // Every alertable event is from the last few hours, so the day feed is enough whatever the
        // screens show. A failed download still checks the cache; it simply finds nothing new.
        earthquakeRepository.refresh(TimePeriod.DAY)
        val feed = earthquakeRepository.feed(TimePeriod.DAY).first().feed ?: return
        val here = locationRepository.lastSaved()?.point
        if (here == null) logger.info(TAG, "No saved location; recording without alerting")
        check(feed.earthquakes, here)
    }

    internal suspend fun check(quakes: List<Earthquake>, here: GeoPoint?) = lock.withLock {
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

    internal companion object {
        /** Names both the periodic work and its registered task. Keep it: devices already have it scheduled. */
        const val BACKGROUND_TASK = "nearby-alerts"

        // 15 minutes is the platform minimum.
        private val BACKGROUND_WORK = PeriodicWork(BACKGROUND_TASK, Duration.ofMinutes(15), requiresNetwork = true)

        private const val TAG = "NearbyAlertChecker"
    }
}
