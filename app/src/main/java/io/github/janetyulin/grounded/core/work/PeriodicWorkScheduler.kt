package io.github.janetyulin.grounded.core.work

import java.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * A unique periodic job that runs the [BackgroundTask] registered under [name], about every [interval]
 * (at least 15 minutes; Doze and standby buckets can defer it much longer), only with a network when
 * [requiresNetwork] is set.
 */
data class PeriodicWork(val name: String, val interval: Duration, val requiresNetwork: Boolean = false)

/**
 * Registers periodic work with the OS, so it runs even when the app isn't. An interface so callers can
 * be tested without WorkManager.
 */
interface PeriodicWorkScheduler {
    /**
     * Schedules [work] unless a job with the same name is already scheduled, so calling this on every
     * app start never resets the period. A changed interval or constraint applies only after [cancel].
     */
    fun schedule(work: PeriodicWork)

    fun cancel(name: String)
}

/**
 * Keeps [work] scheduled while [enabled] is true and cancelled while it is false, for as long as the
 * caller collects. Suspends until cancelled.
 */
suspend fun PeriodicWorkScheduler.keepScheduledWhile(enabled: Flow<Boolean>, work: PeriodicWork) {
    enabled.distinctUntilChanged().collect { on -> if (on) schedule(work) else cancel(work.name) }
}
