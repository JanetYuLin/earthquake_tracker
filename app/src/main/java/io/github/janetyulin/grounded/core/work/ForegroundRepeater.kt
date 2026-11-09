package io.github.janetyulin.grounded.core.work

import io.github.janetyulin.grounded.core.lifecycle.AppForegroundMonitor
import io.github.janetyulin.grounded.core.logging.LoggingManager
import java.time.Duration
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Repeats in-process work on a timer, but only while the app is in the foreground.
 */
class ForegroundRepeater @Inject constructor(
    private val foregroundMonitor: AppForegroundMonitor,
    private val logger: LoggingManager
) {
    /**
     * Runs [action] with the latest [input] right away, then every [interval], while the app is in the
     * foreground. Going to the background stops the loop, so no timers or requests run; returning to
     * the foreground, or a new [input], starts it again immediately. Suspends until cancelled.
     *
     * [tag] names the caller in the log.
     */
    suspend fun <T> repeatWhileForeground(
        tag: String,
        input: Flow<T>,
        interval: Duration,
        action: suspend (T) -> Unit
    ) {
        val latest = input.distinctUntilChanged()
        combine(foregroundMonitor.isForeground, latest) { foreground, value -> foreground to value }
            .distinctUntilChanged()
            .collectLatest { (foreground, value) ->
                if (!foreground) {
                    logger.info(tag, "Paused")
                    return@collectLatest
                }
                logger.info(tag, "Repeating every ${interval.toMinutes()} min")
                while (true) {
                    action(value)
                    delay(interval.toMillis().milliseconds)
                }
            }
    }
}
