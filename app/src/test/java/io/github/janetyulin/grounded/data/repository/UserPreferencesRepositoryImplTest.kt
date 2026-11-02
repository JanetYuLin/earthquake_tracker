package io.github.janetyulin.grounded.data.repository

import io.github.janetyulin.grounded.core.logging.LoggingManager
import io.github.janetyulin.grounded.data.model.UserPreferences
import io.github.janetyulin.grounded.domain.model.TimePeriod
import io.github.janetyulin.grounded.testing.InMemoryDataStore
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UserPreferencesRepositoryImplTest {

    private val dataStore = InMemoryDataStore(UserPreferences())
    private val repository = UserPreferencesRepositoryImpl(dataStore, mockk<LoggingManager>(relaxUnitFun = true))

    @Test
    fun `selected period and alert preferences are saved`() = runTest {
        repository.setSelectedPeriod(TimePeriod.WEEK)
        repository.updateAlertPreferences { it.copy(enabled = true, radiusKm = 300) }

        with(dataStore.value) {
            assertEquals(TimePeriod.WEEK, selectedPeriod)
            assertEquals(true, alerts.enabled)
            assertEquals(300, alerts.radiusKm)
        }
    }
}
