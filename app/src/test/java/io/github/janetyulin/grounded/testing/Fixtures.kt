package io.github.janetyulin.grounded.testing

import io.github.janetyulin.grounded.data.model.Earthquake
import io.github.janetyulin.grounded.domain.model.AlertLevel

/** Reads a file from `src/test/resources`. */
fun readResource(path: String): String =
    checkNotNull(Thread.currentThread().contextClassLoader?.getResource(path)) { "Missing resource $path" }.readText()

fun earthquake(
    id: String = "us1",
    magnitude: Double = 4.2,
    latitude: Double = 34.0,
    longitude: Double = -118.0,
    timeMillis: Long = 0L,
    place: String = "Somewhere",
    alertLevel: AlertLevel? = null,
    tsunamiFlag: Boolean = false
) = Earthquake(
    id = id,
    title = "M $magnitude - $place",
    magnitude = magnitude,
    place = place,
    timeMillis = timeMillis,
    updatedMillis = timeMillis,
    longitude = longitude,
    latitude = latitude,
    depthKm = 10.0,
    url = "https://example.com/$id",
    alertLevel = alertLevel,
    tsunamiFlag = tsunamiFlag,
    feltReports = null,
    reviewed = false
)
