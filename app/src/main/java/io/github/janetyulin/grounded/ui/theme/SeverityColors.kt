package io.github.janetyulin.grounded.ui.theme

import androidx.compose.ui.graphics.Color
import io.github.janetyulin.grounded.domain.model.Severity

/**
 * Fill and text colors for a [Severity].
 */
data class SeverityColors(val container: Color, val content: Color)

fun Severity.colors(): SeverityColors = when (this) {
    Severity.MINOR -> SeverityColors(severityMinor, onSeverityMinor)
    Severity.LIGHT -> SeverityColors(severityLight, onSeverityLight)
    Severity.MODERATE -> SeverityColors(severityModerate, onSeverityModerate)
    Severity.STRONG -> SeverityColors(severityStrong, onSeverityStrong)
    Severity.MAJOR -> SeverityColors(severityMajor, onSeverityMajor)
}
