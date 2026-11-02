package io.github.janetyulin.grounded.data.model

import kotlinx.serialization.Serializable

/**
 * Bookkeeping for nearby alerts, persisted so a background check in a fresh process behaves like
 * the foreground one: it knows what was already notified and which settings the ids were primed for.
 *
 * @param notifiedEarthquakeIds Most recent ids already handled, newest last; capped.
 * @param primedFor Settings the recorded ids were primed for; `null` until the first check.
 */
@Serializable
data class AlertState(val notifiedEarthquakeIds: List<String> = emptyList(), val primedFor: AlertPrimingKey? = null)

/**
 * Settings that, when changed, re-prime instead of notifying the backlog. Deliberately excludes the
 * device location (it changes slightly on every fix) and the feed period (every alertable event is
 * recent, so it is in every feed).
 */
@Serializable
data class AlertPrimingKey(val radiusKm: Int, val minMagnitude: Double)

/**
 * The last device fix seen while the app was open, so background alert checks (which may not ask for
 * the location) have somewhere to measure from. Coarse, on-device only, excluded from backup.
 */
@Serializable
data class LastLocation(val latitude: Double? = null, val longitude: Double? = null, val fixedAtMillis: Long = 0L)
