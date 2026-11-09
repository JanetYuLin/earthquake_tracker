package io.github.janetyulin.grounded.alerts

import io.github.janetyulin.grounded.data.model.AlertPreferences
import io.github.janetyulin.grounded.data.model.AlertPrimingKey
import io.github.janetyulin.grounded.data.model.AlertState
import io.github.janetyulin.grounded.data.model.Earthquake
import io.github.janetyulin.grounded.data.repository.EarthquakeRepository
import io.github.janetyulin.grounded.domain.model.GeoPoint
import java.time.Instant
import kotlin.math.roundToInt

/**
 * The nearby-alert rules, as a pure function shared by the foreground and background checks.
 *
 * - An event alerts when it is within [AlertPreferences.radiusKm] of [here], at or above the minimum
 *   magnitude, no older than [EarthquakeRepository.RECENT_WINDOW], and not handled before.
 * - The first check for a [AlertPrimingKey], or any check while alerts are off or [here] is unknown,
 *   only records ids, so existing events are never replayed as new.
 * - Events below the minimum magnitude are **not** recorded: USGS often revises automatic magnitudes
 *   upward, so an event that later crosses the threshold still alerts.
 */
internal fun decideAlerts(
    quakes: List<Earthquake>,
    here: GeoPoint?,
    alerts: AlertPreferences,
    state: AlertState,
    now: Instant
): AlertDecision {
    val key = AlertPrimingKey(alerts.radiusKm, alerts.minMagnitude)
    val seen = state.notifiedEarthquakeIds.toSet()
    val unseen = quakes.filter { it.id !in seen }
    if (state.primedFor != key || !alerts.enabled || here == null) {
        return AlertDecision(key, record = unseen.map { it.id }, notify = emptyList())
    }
    val cutoff = now.minus(EarthquakeRepository.RECENT_WINDOW)
    val toNotify = unseen
        .filter { it.magnitude >= alerts.minMagnitude }
        .filter { Instant.ofEpochMilli(it.timeMillis) >= cutoff }
        .map { it to here.distanceKmTo(it.location) }
        .filter { (_, distanceKm) -> distanceKm <= alerts.radiusKm }
        .map { (quake, distanceKm) -> EarthquakeAlert(quake.id, quake.magnitude, quake.place, distanceKm.roundToInt()) }
    return AlertDecision(key, record = toNotify.map { it.earthquakeId }, notify = toNotify)
}
