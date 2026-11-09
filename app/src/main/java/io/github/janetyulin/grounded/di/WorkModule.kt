package io.github.janetyulin.grounded.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import io.github.janetyulin.grounded.core.lifecycle.AppForegroundMonitor
import io.github.janetyulin.grounded.core.lifecycle.ProcessLifecycleForegroundMonitor
import io.github.janetyulin.grounded.core.work.BackgroundTask
import io.github.janetyulin.grounded.core.work.PeriodicWorkScheduler
import io.github.janetyulin.grounded.core.work.WorkManagerPeriodicWorkScheduler

/**
 * Feature-independent scheduling: the foreground monitor, the WorkManager scheduler, and the map of
 * [BackgroundTask]s that features contribute to with `@IntoMap @StringKey`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WorkModule {

    @Binds
    abstract fun bindAppForegroundMonitor(impl: ProcessLifecycleForegroundMonitor): AppForegroundMonitor

    @Binds
    abstract fun bindPeriodicWorkScheduler(impl: WorkManagerPeriodicWorkScheduler): PeriodicWorkScheduler

    /** Declared so the map exists even before any feature registers a task. */
    @Multibinds
    abstract fun backgroundTasks(): Map<String, BackgroundTask>
}
