package io.github.janetyulin.grounded.data.remote.earthquake

import kotlinx.serialization.Serializable

/**
 * GeoJSON Network Response Data Transfer Objects (DTOs) for USGS API parsing.
 */
@Serializable
data class USGSFeedDto(val type: String, val metadata: USGSMetadataDto? = null, val features: List<USGSFeatureDto>)

@Serializable
data class USGSMetadataDto(val generated: Long, val url: String, val title: String, val count: Int)

@Serializable
data class USGSFeatureDto(val id: String, val properties: USGSPropertiesDto, val geometry: USGSGeometryDto)

@Serializable
data class USGSPropertiesDto(
    val mag: Double? = null,
    val place: String? = null,
    val time: Long,
    val updated: Long? = null,
    val title: String? = null,
    val url: String,
    val alert: String? = null,
    val tsunami: Int = 0,
    val felt: Int? = null,
    val status: String? = null
)

@Serializable
data class USGSGeometryDto(val type: String, val coordinates: List<Double>)
