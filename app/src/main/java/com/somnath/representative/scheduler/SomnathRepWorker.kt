package com.somnath.representative.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.somnath.representative.data.SchedulerPrefs

class SomnathRepWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            SchedulerPrefs.recordScheduledCycle(applicationContext)
            Result.success()
        } catch (e: Exception) {
            SchedulerPrefs.incrementErrors(applicationContext)
            Result.retry()
        }
    }
}
