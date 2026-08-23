package com.maelle.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.maelle.core.logging.RedactingLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ReconcileDownloadsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val logger: RedactingLogger,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        logger.i(
            component = "ReconcileWorker",
            message = "Startup reconciliation placeholder executed",
        )
        return Result.success()
    }
}
