package io.github.janetyulin.grounded.domain.model

/**
 * Product limits and defaults for nearby-earthquake alerts. Shared by the stored preferences and the
 * Profile screen so the two can't drift apart.
 */
object AlertRules {
    const val DEFAULT_MIN_MAGNITUDE = 3.5
    const val DEFAULT_RADIUS_KM = 200

    val MAGNITUDE_OPTIONS = listOf(2.5, 3.5, 4.5, 5.5)

    /** Search and alert radius bounds, inclusive. */
    val RADIUS_KM_RANGE = 50..500
    const val RADIUS_STEP_KM = 25
}
