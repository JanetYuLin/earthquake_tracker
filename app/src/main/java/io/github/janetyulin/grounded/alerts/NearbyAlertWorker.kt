package io.github.janetyulin.grounded.alerts

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic background alert check. Thin on purpose: the logic is [BackgroundAlertTrigger], so it can
 * be tested without WorkManager.
 */
@HiltWorker
class NearbyAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val check: BackgroundAlertTrigger
) : CoroutineWorker(context, params) {

    // Always success: the work is periodic, so the next run is the retry. Failures are logged by
    // the repository and surface in the app as "Couldn't refresh".
    override suspend fun doWork(): Result {
        check.run()
        return Result.success()
    }
}
