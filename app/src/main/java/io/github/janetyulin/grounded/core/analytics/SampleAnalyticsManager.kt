package io.github.janetyulin.grounded.core.analytics

import io.github.janetyulin.grounded.core.logging.LoggingManager
import javax.inject.Inject

/**
 * Sample [AnalyticsManager] that records events to the local log instead of a backend.
 */
class SampleAnalyticsManager @Inject constructor(private val logger: LoggingManager) : AnalyticsManager {

    override fun trackScreen(screenName: String) {
        logger.info(TAG, "trackScreen: $screenName")
    }

    private companion object {
        const val TAG = "Analytics"
    }
}
