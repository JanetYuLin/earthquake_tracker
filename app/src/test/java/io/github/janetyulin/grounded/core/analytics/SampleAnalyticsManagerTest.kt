package io.github.janetyulin.grounded.core.analytics

import io.github.janetyulin.grounded.core.logging.LoggingManager
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class SampleAnalyticsManagerTest {

    private val logger = mockk<LoggingManager>(relaxUnitFun = true)
    private val analyticsManager = SampleAnalyticsManager(logger)

    @Test
    fun `trackScreen logs the screen name locally`() {
        analyticsManager.trackScreen("home")

        verify(exactly = 1) { logger.info("Analytics", "trackScreen: home") }
    }
}
