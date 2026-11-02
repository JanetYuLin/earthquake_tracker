package io.github.janetyulin.grounded.data.repository

import io.github.janetyulin.grounded.core.logging.LoggingManager
import io.github.janetyulin.grounded.data.model.AlertPrimingKey
import io.github.janetyulin.grounded.data.model.AlertState
import io.github.janetyulin.grounded.testing.InMemoryDataStore
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertStateRepositoryImplTest {

    private val dataStore = InMemoryDataStore(AlertState())
    private val repository = AlertStateRepositoryImpl(dataStore, mockk<LoggingManager>(relaxUnitFun = true))
    private val key = AlertPrimingKey(radiusKm = 200, minMagnitude = 3.5)

    @Test
    fun `record keeps the newest ids, without duplicates, and the priming key`() = runTest {
        repository.record(key, listOf("a", "b"))
        repository.record(key, listOf("a", "c"))

        assertEquals(AlertState(listOf("b", "a", "c"), key), repository.current())
    }

    @Test
    fun `record caps the ids it keeps`() = runTest {
        repository.record(key, (1..AlertStateRepository.MAX_NOTIFIED_IDS + 5).map { "n$it" })

        with(repository.current().notifiedEarthquakeIds) {
            assertEquals(AlertStateRepository.MAX_NOTIFIED_IDS, size)
            assertEquals("n${AlertStateRepository.MAX_NOTIFIED_IDS + 5}", last())
        }
    }
}
