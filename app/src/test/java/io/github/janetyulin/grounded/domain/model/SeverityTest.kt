package io.github.janetyulin.grounded.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SeverityTest {

    @Test
    fun `buckets magnitudes at inclusive lower bounds`() {
        assertEquals(Severity.MINOR, Severity.fromMagnitude(-0.5))
        assertEquals(Severity.MINOR, Severity.fromMagnitude(2.99))
        assertEquals(Severity.LIGHT, Severity.fromMagnitude(3.0))
        assertEquals(Severity.MODERATE, Severity.fromMagnitude(4.0))
        assertEquals(Severity.STRONG, Severity.fromMagnitude(5.5))
        assertEquals(Severity.MAJOR, Severity.fromMagnitude(6.0))
        assertEquals(Severity.MAJOR, Severity.fromMagnitude(9.1))
    }
}
