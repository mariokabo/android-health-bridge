package com.airhealth.bridge.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val UNIQUE_PERIODIC_SYNC = "airhealth_periodic_sync"

    fun schedulePeriodic(context: Context, intervalMinutes: Long) {
        val periodic = PeriodicWorkRequestBuilder<SyncWorker>(
            intervalMinutes.coerceAtLeast(15),
            TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_SYNC,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic
        )
    }

    fun syncNow(context: Context) {
        val oneTime = OneTimeWorkRequestBuilder<SyncWorker>().build()
        WorkManager.getInstance(context).enqueue(oneTime)
    }
}
