package io.github.janetyulin.grounded.alerts

import io.github.janetyulin.grounded.core.logging.LoggingManager
import io.github.janetyulin.grounded.data.model.AlertPreferences
import io.github.janetyulin.grounded.data.model.AlertState
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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundAlertTriggerTest {

    private val now = Instant.parse("2026-10-31T12:00:00Z")
    private val recently = now.minus(Duration.ofMinutes(10)).toEpochMilli()
    private val pasadena = GeoPoint(34.15, -118.14)
    private val logger = mockk<LoggingManager>(relaxUnitFun = true)

    private val preferencesStore = InMemoryDataStore(
        UserPreferences(
            alerts = AlertPreferences(enabled = true, minMagnitude = 3.5, radiusKm = 200),
            selectedPeriod = TimePeriod.WEEK
        )
    )
    private val preferencesRepository = UserPreferencesRepositoryImpl(preferencesStore, logger)
    private val alertStateRepository = AlertStateRepositoryImpl(InMemoryDataStore(AlertState()), logger)
    private val earthquakeRepository = FakeEarthquakeRepository()

    // The live location is unknown in the background; only the saved one is available.
    private val locationRepository = FakeLocationRepository(UserLocation.Unknown).apply {
        saved = UserLocation.Available(pasadena, now)
    }

    private val posted = mutableListOf<EarthquakeAlert>()
    private val publisher = object : NotificationPublisher {
        override fun createChannels() = Unit
        override fun showEarthquakeAlert(alert: EarthquakeAlert) {
            posted += alert
        }
    }
    private val alertCheck =
        NearbyAlertCheck(
            preferencesRepository,
            alertStateRepository,
            publisher,
            Clock.fixed(now, ZoneOffset.UTC),
            logger
        )
    private val check =
        BackgroundAlertTrigger(preferencesRepository, earthquakeRepository, locationRepository, alertCheck, logger)

    private val existing = earthquake("existing", magnitude = 4.0, timeMillis = recently)
    private val nearby =
        earthquake(
            "near",
            magnitude = 4.8,
            latitude = 34.15,
            longitude = -118.14,
            place = "Pasadena",
            timeMillis = recently
        )

    @Test
    fun `refreshes the day feed, whatever period the screens show, and alerts from the saved location`() = runTest {
        earthquakeRepository.nextRefresh = listOf(existing)
        check.run()
        earthquakeRepository.nextRefresh = listOf(nearby, existing)

        check.run()

        assertEquals(listOf(TimePeriod.DAY, TimePeriod.DAY), earthquakeRepository.refreshCalls)
        assertEquals(listOf(EarthquakeAlert("near", 4.8, "Pasadena", 0)), posted)
    }

    @Test
    fun `does no work while alerts are off`() = runTest {
        preferencesStore.updateData { it.copy(alerts = it.alerts.copy(enabled = false)) }

        check.run()

        assertTrue(earthquakeRepository.refreshCalls.isEmpty())
    }

    @Test
    fun `without a saved location it records but never alerts`() = runTest {
        locationRepository.saved = null
        earthquakeRepository.nextRefresh = listOf(existing)
        check.run()
        earthquakeRepository.nextRefresh = listOf(nearby, existing)

        check.run()

        assertTrue(posted.isEmpty())
    }
}
