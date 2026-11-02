package io.github.janetyulin.grounded.core.analytics

/**
 * App-wide analytics abstraction. Callers never depend on a vendor SDK directly.
 */
interface AnalyticsManager {
    /**
     * Records that the user viewed a screen.
     * @param screenName Stable, human-readable name. Never derive it from class names,
     * which R8 obfuscates in release builds.
     */
    fun trackScreen(screenName: String)
}
