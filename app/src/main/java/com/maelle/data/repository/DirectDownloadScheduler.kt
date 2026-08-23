package com.maelle.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DirectDownloadScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun enqueue(jobId: String) {
        val request = OneTimeWorkRequestBuilder<com.maelle.workers.DirectDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(workDataOf(com.maelle.workers.DirectDownloadWorker.KEY_JOB_ID to jobId))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "direct-download-$jobId",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
