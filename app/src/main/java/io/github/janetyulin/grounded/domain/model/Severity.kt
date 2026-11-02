package io.github.janetyulin.grounded.domain.model

/**
 * Magnitude buckets shared by list badges and the map legend.
 * @param minMagnitude Inclusive lower bound of the bucket.
 */
enum class Severity(val minMagnitude: Double) {
    MINOR(Double.NEGATIVE_INFINITY),
    LIGHT(3.0),
    MODERATE(4.0),
    STRONG(5.0),
    MAJOR(6.0);

    companion object {
        fun fromMagnitude(magnitude: Double): Severity = entries.last { magnitude >= it.minMagnitude }
    }
}
