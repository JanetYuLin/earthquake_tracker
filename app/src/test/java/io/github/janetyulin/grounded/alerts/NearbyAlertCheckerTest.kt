package io.github.janetyulin.grounded.alerts

import io.github.janetyulin.grounded.core.logging.LoggingManager
import io.github.janetyulin.grounded.core.work.PeriodicWork
import io.github.janetyulin.grounded.core.work.PeriodicWorkScheduler
import io.github.janetyulin.grounded.data.model.AlertPreferences
import io.github.janetyulin.grounded.data.model.AlertState
import io.github.janetyulin.grounded.data.model.Earthquake
import io.github.janetyulin.grounded.data.model.UserLocation
import io.github.janetyulin.grounded.data.model.UserPreferences
import io.github.janetyulin.grounded.data.repository.AlertStateRepositoryImpl
import io.github.janetyulin.grounded.data.repository.UserPreferencesRepositoryImpl
import io.github.janetyulin.grounded.domain.model.GeoPoint
import io.github.janetyulin.grounded.domain.model.TimePeriod
import io.github.janetyulin.grounded.testing.FakeEarthquakeRepository
import io.github.janetyulin.grounded.testing.FakeLocationRepository
import io.github.janetyulin.grounded.testing.InMemoryDataStore
import io.github.janetyulin.grounded.testing.earthquake
import io.mockk.mockk
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NearbyAlertCheckerTest {

    private val now = Instant.parse("2026-10-31T12:00:00Z")
    private val recently = now.minus(Duration.ofMinutes(10)).toEpochMilli()
    private val pasadena = GeoPoint(34.15, -118.14)
    private val logger = mockk<LoggingManager>(relaxUnitFun = true)

    private val dataStore = InMemoryDataStore(
        UserPreferences(alerts = AlertPreferences(enabled = true, minMagnitude = 3.5, radiusKm = 200))
    )
    private val preferencesRepository = UserPreferencesRepositoryImpl(dataStore, logger)
    private val alertStore = InMemoryDataStore(AlertState())
    private val alertStateRepository = AlertStateRepositoryImpl(alertStore, logger)
    private val earthquakeRepository = FakeEarthquakeRepository()
    private val locationRepository = FakeLocationRepository(UserLocation.Available(pasadena, now))

    private val posted = mutableListOf<EarthquakeAlert>()
    private val recordedBeforePosting = mutableListOf<Boolean>()
    private val publisher = object : NotificationPublisher {
        override fun createChannels() = Unit
        override fun showEarthquakeAlert(alert: EarthquakeAlert) {
            recordedBeforePosting += alert.earthquakeId in alertStore.value.notifiedEarthquakeIds
            posted += alert
        }
    }

    private val scheduleCalls = mutableListOf<String>()
    private val scheduled = mutableListOf<PeriodicWork>()
    private val workScheduler = object : PeriodicWorkScheduler {
        override fun schedule(work: PeriodicWork) {
            scheduleCalls += "schedule ${work.name}"
            scheduled += work
        }
        override fun cancel(name: String) {
            scheduleCalls += "cancel $name"
        }
    }

    private val existing = earthquake("existing", magnitude = 4.0, timeMillis = recently)
    private val bigNew =
        earthquake(
            "big",
            magnitude = 4.8,
            latitude = 34.15,
            longitude = -118.14,
            place = "Pasadena",
            timeMillis = recently
        )
    private val smallNew = earthquake("small", magnitude = 2.0, timeMillis = recently)
    private val farAway =
        earthquake("tokyo", magnitude = 6.0, latitude = 35.68, longitude = 139.69, timeMillis = recently)
    private val hoursAgo =
        earthquake("old", magnitude = 5.0, timeMillis = now.minus(Duration.ofHours(6)).toEpochMilli())

    /** A fresh checker has no in-memory state, like one in a new process (e.g. a background worker). */
    private fun TestScope.newChecker() = NearbyAlertChecker(
        preferencesRepository,
        earthquakeRepository,
        locationRepository,
        alertStateRepository,
        publisher,
        workScheduler,
        Clock.fixed(now, ZoneOffset.UTC),
        backgroundScope,
        logger
    )

    private fun TestScope.startObserving() {
        val checker = newChecker()
        backgroundScope.launch { checker.observeForeground() }
        runCurrent()
    }

    private fun TestScope.emit(quakes: List<Earthquake>, period: TimePeriod = TimePeriod.DAY) {
        earthquakeRepository.setFeed(period, quakes)
        runCurrent()
    }

    // Foreground

    @Test
    fun `first feed after start only records ids`() = runTest {
        startObserving()

        emit(listOf(existing))

        assertTrue(posted.isEmpty())
        assertEquals(listOf("existing"), alertStore.value.notifiedEarthquakeIds)
    }

    @Test
    fun `notifies new nearby events at or above the minimum magnitude, with the distance`() = runTest {
        startObserving()
        emit(listOf(existing))

        emit(listOf(bigNew, smallNew, existing))

        assertEquals(listOf(EarthquakeAlert("big", 4.8, "Pasadena", 0)), posted)
        // Below-threshold events stay unrecorded so a later upward revision can still alert.
        assertTrue("big" in alertStore.value.notifiedEarthquakeIds)
        assertFalse("small" in alertStore.value.notifiedEarthquakeIds)
    }

    @Test
    fun `ignores events outside the radius or older than the recent window`() = runTest {
        startObserving()
        emit(listOf(existing))

        emit(listOf(farAway, hoursAgo, existing))

        assertTrue(posted.isEmpty())
    }

    @Test
    fun `ids are recorded before posting so a failed post can't re-notify`() = runTest {
        startObserving()
        emit(listOf(existing))

        emit(listOf(bigNew, existing))

        assertEquals(listOf(true), recordedBeforePosting)
    }

    @Test
    fun `never notifies the same event twice`() = runTest {
        startObserving()
        emit(listOf(existing))
        emit(listOf(bigNew, existing))

        emit(listOf(bigNew, existing))

        assertEquals(1, posted.size)
    }

    @Test
    fun `records but does not notify while alerts are disabled`() = runTest {
        dataStore.updateData { it.copy(alerts = it.alerts.copy(enabled = false)) }
        startObserving()
        emit(listOf(existing))

        emit(listOf(bigNew, existing))
        dataStore.updateData { it.copy(alerts = it.alerts.copy(enabled = true)) }
        emit(listOf(bigNew, existing))

        assertTrue(posted.isEmpty())
    }

    @Test
    fun `without a location nothing is notified, and the backlog doesn't replay once it arrives`() = runTest {
        locationRepository.set(UserLocation.PermissionRequired)
        startObserving()
        emit(listOf(existing))

        emit(listOf(bigNew, existing))
        locationRepository.set(UserLocation.Available(pasadena, now))
        runCurrent()

        assertTrue(posted.isEmpty())
    }

    @Test
    fun `a slightly different location fix doesn't re-prime`() = runTest {
        startObserving()
        emit(listOf(existing))

        locationRepository.set(UserLocation.Available(GeoPoint(34.16, -118.13), now))
        runCurrent()
        emit(listOf(bigNew, existing))

        assertEquals(listOf("big"), posted.map { it.earthquakeId })
    }

    @Test
    fun `a new radius re-primes instead of notifying its backlog`() = runTest {
        startObserving()
        emit(listOf(existing))

        dataStore.updateData { it.copy(alerts = it.alerts.copy(radiusKm = 400)) }
        emit(listOf(bigNew, existing))

        assertTrue(posted.isEmpty())
    }

    @Test
    fun `switching the period keeps alerting new events without re-priming`() = runTest {
        startObserving()
        emit(listOf(existing))

        dataStore.updateData { it.copy(selectedPeriod = TimePeriod.WEEK) }
        emit(listOf(bigNew, existing, hoursAgo), period = TimePeriod.WEEK)

        assertEquals(listOf("big"), posted.map { it.earthquakeId })
    }

    @Test
    fun `an event revised above the threshold alerts once`() = runTest {
        startObserving()
        emit(listOf(existing))

        emit(listOf(smallNew.copy(magnitude = 3.3), existing))
        emit(listOf(smallNew.copy(magnitude = 3.8), existing))
        emit(listOf(smallNew.copy(magnitude = 4.1), existing))

        assertEquals(listOf("small"), posted.map { it.earthquakeId })
    }

    @Test
    fun `lowering the threshold re-primes instead of notifying smaller existing events`() = runTest {
        startObserving()
        emit(listOf(existing))
        emit(listOf(smallNew, existing))

        dataStore.updateData { it.copy(alerts = it.alerts.copy(minMagnitude = 1.0)) }
        emit(listOf(smallNew, existing))

        assertTrue(posted.isEmpty())
    }

    @Test
    fun `a failed refresh keeps the cache, so nothing is replayed or lost`() = runTest {
        startObserving()
        emit(listOf(existing))

        earthquakeRepository.nextRefresh = null
        earthquakeRepository.refresh(TimePeriod.DAY)
        runCurrent()
        emit(listOf(bigNew, existing))

        assertEquals(listOf("big"), posted.map { it.earthquakeId })
    }

    // Background

    /** In the background the live location is unknown; only the saved one is available. */
    private fun inBackground() {
        locationRepository.set(UserLocation.Unknown)
        locationRepository.saved = UserLocation.Available(pasadena, now)
    }

    @Test
    fun `background refreshes the day feed, whatever period the screens show, and alerts from the saved location`() =
        runTest {
            inBackground()
            dataStore.updateData { it.copy(selectedPeriod = TimePeriod.WEEK) }
            val checker = newChecker()
            earthquakeRepository.nextRefresh = listOf(existing)
            checker.checkInBackground()
            earthquakeRepository.nextRefresh = listOf(bigNew, existing)

            checker.checkInBackground()

            assertEquals(listOf(TimePeriod.DAY, TimePeriod.DAY), earthquakeRepository.refreshCalls)
            assertEquals(listOf(EarthquakeAlert("big", 4.8, "Pasadena", 0)), posted)
        }

    @Test
    fun `background does no work while alerts are off`() = runTest {
        inBackground()
        dataStore.updateData { it.copy(alerts = it.alerts.copy(enabled = false)) }

        newChecker().checkInBackground()

        assertTrue(earthquakeRepository.refreshCalls.isEmpty())
    }

    @Test
    fun `background without a saved location records but never alerts`() = runTest {
        inBackground()
        locationRepository.saved = null
        val checker = newChecker()
        earthquakeRepository.nextRefresh = listOf(existing)
        checker.checkInBackground()
        earthquakeRepository.nextRefresh = listOf(bigNew, existing)

        checker.checkInBackground()

        assertTrue(posted.isEmpty())
    }

    @Test
    fun `background checks are scheduled every 15 minutes with a network, only while alerts are on`() = runTest {
        dataStore.updateData { it.copy(alerts = it.alerts.copy(enabled = false)) }
        val checker = newChecker()
        backgroundScope.launch { checker.scheduleBackgroundChecks() }
        runCurrent()

        preferencesRepository.updateAlertPreferences { it.copy(enabled = true) }
        runCurrent()
        preferencesRepository.updateAlertPreferences { it.copy(radiusKm = 300) }
        runCurrent()
        preferencesRepository.updateAlertPreferences { it.copy(enabled = false) }
        runCurrent()

        // Unrelated changes (the radius) don't reschedule.
        assertEquals(listOf("cancel nearby-alerts", "schedule nearby-alerts", "cancel nearby-alerts"), scheduleCalls)
        assertEquals(listOf(PeriodicWork("nearby-alerts", Duration.ofMinutes(15), requiresNetwork = true)), scheduled)
    }

    // Both paths

    @Test
    fun `priming survives a new process, so a background check alerts new events`() = runTest {
        newChecker().check(listOf(existing), pasadena)

        // A later check with no memory of the first, as a worker in a fresh process would be.
        newChecker().check(listOf(bigNew, existing), pasadena)

        assertEquals(listOf("big"), posted.map { it.earthquakeId })
    }

    @Test
    fun `foreground and background checks never post the same event twice`() = runTest {
        newChecker().check(listOf(existing), pasadena)
        val shared = newChecker()

        backgroundScope.launch { shared.check(listOf(bigNew, existing), pasadena) }
        backgroundScope.launch { shared.check(listOf(bigNew, existing), pasadena) }
        runCurrent()

        assertEquals(listOf("big"), posted.map { it.earthquakeId })
    }
}
