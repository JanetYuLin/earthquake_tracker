package io.github.janetyulin.grounded.core.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.janetyulin.grounded.core.logging.LoggingManager
import javax.inject.Inject

/**
 * [PeriodicWorkScheduler] backed by WorkManager unique periodic work, run by [BackgroundTaskWorker].
 */
class WorkManagerPeriodicWorkScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logger: LoggingManager
) : PeriodicWorkScheduler {

    override fun schedule(work: PeriodicWork) {
        val constraints = Constraints.Builder()
            .apply { if (work.requiresNetwork) setRequiredNetworkType(NetworkType.CONNECTED) }
            .build()
        val request = PeriodicWorkRequest.Builder(BackgroundTaskWorker::class.java, work.interval)
            .setConstraints(constraints)
            .setInputData(workDataOf(BackgroundTaskWorker.KEY_TASK to work.name))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(work.name, ExistingPeriodicWorkPolicy.KEEP, request)
        logger.info(TAG, "Scheduled ${work.name}")
    }

    override fun cancel(name: String) {
        WorkManager.getInstance(context).cancelUniqueWork(name)
        logger.info(TAG, "Cancelled $name")
    }

    private companion object {
        const val TAG = "PeriodicWork"
    }
}
