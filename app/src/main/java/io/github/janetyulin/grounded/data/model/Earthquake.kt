package io.github.janetyulin.grounded.data.model

import io.github.janetyulin.grounded.domain.model.AlertLevel
import io.github.janetyulin.grounded.domain.model.GeoPoint
import kotlinx.serialization.Serializable

/**
 * One earthquake event, as the app uses it. Serializable so feeds can be cached on disk.
 */
@Serializable
data class Earthquake(
    val id: String,
    val title: String,
    val magnitude: Double,
    val place: String,
    val timeMillis: Long,
    val updatedMillis: Long,
    val longitude: Double,
    val latitude: Double,
    val depthKm: Double,
    val url: String,
    val alertLevel: AlertLevel?,
    val tsunamiFlag: Boolean,
    val feltReports: Int?,
    val reviewed: Boolean
) {
    val location: GeoPoint get() = GeoPoint(latitude, longitude)
}
