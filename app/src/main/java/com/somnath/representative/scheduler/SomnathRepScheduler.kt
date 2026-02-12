package com.somnath.representative.scheduler

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object SomnathRepScheduler {
    const val UNIQUE_WORK_NAME = "somnath_rep_scheduler"
    const val RUN_NOW_WORK_NAME = "somnath_rep_run_now"

    fun schedule(context: Context, chargingOnly: Boolean, wifiOnly: Boolean) {
        val constraintsBuilder = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)

        if (chargingOnly) {
            constraintsBuilder.setRequiresCharging(true)
        }

        val initialDelayMinutes = Random.nextLong(0, 76)

        val request = PeriodicWorkRequestBuilder<SomnathRepWorker>(45, TimeUnit.MINUTES)
            .setConstraints(constraintsBuilder.build())
            .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    fun runNow(context: Context, chargingOnly: Boolean, wifiOnly: Boolean) {
        val constraintsBuilder = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)

        if (chargingOnly) {
            constraintsBuilder.setRequiresCharging(true)
        }

        val request = OneTimeWorkRequestBuilder<SomnathRepWorker>()
            .setConstraints(constraintsBuilder.build())
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            RUN_NOW_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
