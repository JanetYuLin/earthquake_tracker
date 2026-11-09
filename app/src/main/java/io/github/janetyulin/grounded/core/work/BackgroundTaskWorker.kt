package io.github.janetyulin.grounded.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.janetyulin.grounded.core.logging.LoggingManager
import javax.inject.Provider

/**
 * The single WorkManager entry point: looks up the [BackgroundTask] named in its input and runs it.
 * Features never write their own worker.
 */
@HiltWorker
class BackgroundTaskWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val tasks: Map<String, @JvmSuppressWildcards Provider<BackgroundTask>>,
    private val logger: LoggingManager
) : CoroutineWorker(context, params) {

    // Always success once the task ran: the work is periodic, so the next run is the retry. Tasks
    // report their own failures.
    override suspend fun doWork(): Result {
        val name = inputData.getString(KEY_TASK)
        val task = tasks[name]?.get()
        if (task == null) {
            logger.warn(TAG, "No background task registered as $name")
            return Result.failure()
        }
        task.run()
        return Result.success()
    }

    internal companion object {
        const val KEY_TASK = "task"
        private const val TAG = "BackgroundTaskWorker"
    }
}
