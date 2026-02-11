package com.somnath.representative.scheduler

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SomnathScheduler {
    const val UNIQUE_WORK_NAME = "somnath_rep_scheduler"

    fun apply(context: Context, chargingOnly: Boolean, wifiOnly: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiresCharging(chargingOnly)
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.NOT_REQUIRED)
            .build()

        val request = PeriodicWorkRequestBuilder<SomnathRepWorker>(45, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )

        SchedulerPrefs(context).setSchedulerEnabled(true)
    }
}
