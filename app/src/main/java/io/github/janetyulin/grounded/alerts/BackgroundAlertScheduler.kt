package io.github.janetyulin.grounded.alerts

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.janetyulin.grounded.core.logging.LoggingManager
import io.github.janetyulin.grounded.data.repository.UserPreferencesRepository
import io.github.janetyulin.grounded.di.ApplicationScope
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Schedules periodic background work. An interface so the scheduling rules can be tested without
 * WorkManager.
 */
interface AlertWorkScheduler {
    fun schedule()
    fun cancel()
}

/**
 * [AlertWorkScheduler] backed by WorkManager: one unique periodic job, every 15 minutes (the
 * platform minimum; Doze and standby buckets can defer it much longer), only with a network.
 */
class WorkManagerAlertScheduler @Inject constructor(@param:ApplicationContext private val context: Context) :
    AlertWorkScheduler {

    override fun schedule() {
        val request = PeriodicWorkRequestBuilder<NearbyAlertWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        // KEEP: re-scheduling on every app start must not reset the period.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "nearby-alerts"
    }
}

/**
 * Keeps the background job in step with the alerts toggle: scheduled while alerts are on, cancelled
 * when they're off, so the app does no background work for users who don't want alerts.
 */
@Singleton
class BackgroundAlertScheduler @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val workScheduler: AlertWorkScheduler,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val logger: LoggingManager
) {
    private var started = false

    /** Starts following the toggle for the lifetime of [scope]. Idempotent; call from the main thread. */
    fun start() {
        if (started) return
        started = true
        scope.launch { run() }
    }

    internal suspend fun run() {
        preferencesRepository.preferences
            .map { it.alerts.enabled }
            .distinctUntilChanged()
            .collect { enabled ->
                if (enabled) workScheduler.schedule() else workScheduler.cancel()
                logger.info(TAG, if (enabled) "Background checks scheduled" else "Background checks cancelled")
            }
    }

    private companion object {
        const val TAG = "BackgroundAlerts"
    }
}
