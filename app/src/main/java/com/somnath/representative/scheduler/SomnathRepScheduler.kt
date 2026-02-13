package com.somnath.representative.scheduler

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.somnath.representative.data.SchedulerPrefs
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object SomnathRepScheduler {
    const val UNIQUE_WORK_NAME = "somnath_rep_scheduler"
    private const val BASE_INTERVAL_MINUTES = 45L

    fun schedule(context: Context, chargingOnly: Boolean, wifiOnly: Boolean) {
        val constraintsBuilder = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)

        if (chargingOnly) {
            constraintsBuilder.setRequiresCharging(true)
        }

        val initialDelayMinutes = Random.nextLong(0, 76)

        val intervalMinutes = SchedulerPrefs.getEffectiveIntervalMinutes(context, BASE_INTERVAL_MINUTES)

        val request = PeriodicWorkRequestBuilder<SomnathRepWorker>(intervalMinutes, TimeUnit.MINUTES)
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
}
