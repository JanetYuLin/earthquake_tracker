package io.github.janetyulin.grounded.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.janetyulin.grounded.R
import io.github.janetyulin.grounded.domain.model.AlertLevel
import io.github.janetyulin.grounded.domain.model.Severity
import io.github.janetyulin.grounded.ui.theme.colors

/**
 * Magnitude in a severity-colored badge. Announces magnitude and severity for screen readers.
 */
@Composable
fun MagnitudeBadge(magnitude: Double, severity: Severity, modifier: Modifier = Modifier) {
    val colors = severity.colors()
    val description = stringResource(R.string.magnitude_description, magnitude, stringResource(severity.labelRes()))
    Box(
        modifier = modifier
            .sizeIn(minWidth = 56.dp, minHeight = 40.dp)
            .background(colors.container, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.earthquake_magnitude, magnitude),
            color = colors.content,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

fun Severity.labelRes(): Int = when (this) {
    Severity.MINOR -> R.string.severity_minor
    Severity.LIGHT -> R.string.severity_light
    Severity.MODERATE -> R.string.severity_moderate
    Severity.STRONG -> R.string.severity_strong
    Severity.MAJOR -> R.string.severity_major
}

fun AlertLevel.labelRes(): Int = when (this) {
    AlertLevel.GREEN -> R.string.pager_green
    AlertLevel.YELLOW -> R.string.pager_yellow
    AlertLevel.ORANGE -> R.string.pager_orange
    AlertLevel.RED -> R.string.pager_red
}

/** Chip for a tsunami-relevant event; links to official guidance rather than asserting a tsunami. */
@Composable
fun TsunamiChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    AssistChip(onClick = onClick, label = { Text(stringResource(R.string.tsunami_chip)) }, modifier = modifier)
}

@Composable
fun relativeTimeText(minutesAgo: Long): String = when {
    minutesAgo < 1 -> stringResource(R.string.time_just_now)

    minutesAgo < 60 -> pluralStringResource(R.plurals.time_minutes_ago, minutesAgo.toInt(), minutesAgo.toInt())

    minutesAgo < 24 * 60 -> pluralStringResource(
        R.plurals.time_hours_ago,
        (minutesAgo / 60).toInt(),
        (minutesAgo / 60).toInt()
    )

    else -> pluralStringResource(
        R.plurals.time_days_ago,
        (minutesAgo / (24 * 60)).toInt(),
        (minutesAgo / (24 * 60)).toInt()
    )
}

/** "2 h ago · 35 km away", or just the time when the distance is unknown. */
@Composable
fun timeAndDistanceText(minutesAgo: Long, distanceKm: Int?): String {
    val time = relativeTimeText(minutesAgo)
    return if (distanceKm == null) time else stringResource(R.string.earthquake_time_distance, time, distanceKm)
}
