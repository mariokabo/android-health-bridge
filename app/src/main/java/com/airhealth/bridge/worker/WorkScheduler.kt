package com.airhealth.bridge.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val UNIQUE_ONE_TIME_SYNC = "airhealth_one_time_sync"

    fun schedulePeriodic(context: Context, intervalMinutes: Long) {
        val delay = intervalMinutes.coerceAtLeast(5)
        val oneTime = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_ONE_TIME_SYNC,
            ExistingWorkPolicy.REPLACE,
            oneTime
        )
    }

    fun syncNow(context: Context) {
        val oneTime = OneTimeWorkRequestBuilder<SyncWorker>().build()
        WorkManager.getInstance(context).enqueue(oneTime)
    }
}
