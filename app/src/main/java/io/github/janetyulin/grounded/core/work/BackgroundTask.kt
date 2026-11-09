package io.github.janetyulin.grounded.core.work

/**
 * One unit of background work, run by [BackgroundTaskWorker]. Register an implementation in a Hilt
 * module with `@IntoMap @StringKey(name)`, and schedule it with a [PeriodicWork] of the same name.
 * Plain Kotlin, so it can be tested without WorkManager.
 */
fun interface BackgroundTask {
    suspend fun run()
}
