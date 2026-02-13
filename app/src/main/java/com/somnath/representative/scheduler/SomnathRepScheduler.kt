package com.somnath.representative.scheduler

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.somnath.representative.data.SchedulerPrefs
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object SomnathRepScheduler {
    const val UNIQUE_WORK_NAME = "somnath_rep_scheduler"
    const val UNIQUE_ADAPTIVE_CHAIN_WORK_NAME = "somnath_rep_adaptive_chain"
    private const val BASE_INTERVAL_MINUTES = 45L

    fun schedule(context: Context, chargingOnly: Boolean, wifiOnly: Boolean) {
        if (SchedulerPrefs.getScheduleMode(context) == SchedulerPrefs.SCHEDULE_MODE_ADAPTIVE_ONE_TIME_CHAIN) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            rescheduleWithAdaptiveDelay(context)
            return
        }

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
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_ADAPTIVE_CHAIN_WORK_NAME)
    }

    fun rescheduleWithAdaptiveDelay(context: Context) {
        if (!SchedulerPrefs.isSchedulerEnabled(context)) {
            return
        }

        val chargingOnly = SchedulerPrefs.isChargingOnly(context)
        val wifiOnly = SchedulerPrefs.isWifiOnly(context)
        val nextDelayMinutes = SchedulerPrefs.getAdaptiveDelayMinutes(context, BASE_INTERVAL_MINUTES)

        val constraintsBuilder = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)

        if (chargingOnly) {
            constraintsBuilder.setRequiresCharging(true)
        }

        val request = OneTimeWorkRequestBuilder<SomnathRepWorker>()
            .setConstraints(constraintsBuilder.build())
            .setInitialDelay(nextDelayMinutes, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_ADAPTIVE_CHAIN_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_ADAPTIVE_CHAIN_WORK_NAME)
    }
}
