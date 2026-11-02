package io.github.janetyulin.grounded.data.remote.earthquake

import io.github.janetyulin.grounded.data.model.Earthquake
import io.github.janetyulin.grounded.domain.model.AlertLevel

/**
 * Mapper extension function from UsgsFeatureDto to domain model Earthquake.
 */
fun USGSFeatureDto.toDomain(): Earthquake {
    val lon = geometry.coordinates.getOrElse(0) { 0.0 }
    val lat = geometry.coordinates.getOrElse(1) { 0.0 }
    val depth = geometry.coordinates.getOrElse(2) { 0.0 }
    return Earthquake(
        id = id,
        title = properties.title ?: properties.place ?: "Earthquake",
        magnitude = properties.mag ?: 0.0,
        place = properties.place ?: "Unknown location",
        timeMillis = properties.time,
        updatedMillis = properties.updated ?: properties.time,
        longitude = lon,
        latitude = lat,
        depthKm = depth,
        url = properties.url,
        alertLevel = properties.alert?.let { level ->
            AlertLevel.entries.firstOrNull { it.name.equals(level, ignoreCase = true) }
        },
        tsunamiFlag = properties.tsunami == 1,
        feltReports = properties.felt,
        reviewed = properties.status.equals("reviewed", ignoreCase = true)
    )
}
