package io.github.janetyulin.grounded.alerts

import io.github.janetyulin.grounded.core.logging.LoggingManager
import io.github.janetyulin.grounded.data.model.UserPreferences
import io.github.janetyulin.grounded.data.repository.UserPreferencesRepositoryImpl
import io.github.janetyulin.grounded.testing.InMemoryDataStore
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundAlertSchedulerTest {

    private val logger = mockk<LoggingManager>(relaxUnitFun = true)
    private val preferencesRepository = UserPreferencesRepositoryImpl(InMemoryDataStore(UserPreferences()), logger)
    private val calls = mutableListOf<String>()
    private val workScheduler = object : AlertWorkScheduler {
        override fun schedule() {
            calls += "schedule"
        }
        override fun cancel() {
            calls += "cancel"
        }
    }

    @Test
    fun `background work follows the alerts toggle, and nothing runs while it is off`() = runTest {
        backgroundScope.launch {
            BackgroundAlertScheduler(preferencesRepository, workScheduler, backgroundScope, logger).run()
        }
        runCurrent()
        assertEquals(listOf("cancel"), calls)

        preferencesRepository.updateAlertPreferences { it.copy(enabled = true) }
        runCurrent()
        preferencesRepository.updateAlertPreferences { it.copy(radiusKm = 300) }
        runCurrent()
        preferencesRepository.updateAlertPreferences { it.copy(enabled = false) }
        runCurrent()

        // Unrelated changes (the radius) don't reschedule.
        assertEquals(listOf("cancel", "schedule", "cancel"), calls)
    }
}
