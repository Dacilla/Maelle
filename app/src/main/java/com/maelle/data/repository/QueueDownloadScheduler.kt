package com.maelle.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueDownloadScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun enqueue(jobId: String) {
        val request = OneTimeWorkRequestBuilder<com.maelle.workers.QueueDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS)
            .setInputData(workDataOf(com.maelle.workers.QueueDownloadWorker.KEY_JOB_ID to jobId))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "queue-download-$jobId",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
