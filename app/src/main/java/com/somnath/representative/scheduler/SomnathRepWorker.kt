package com.somnath.representative.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlin.random.Random

class SomnathRepWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = SchedulerPrefs(applicationContext)
        return try {
            val now = System.currentTimeMillis()
            val nextEligibleAt = prefs.getNextEligibleAtMillis()

            if (nextEligibleAt != 0L && now < nextEligibleAt) {
                return Result.success()
            }

            prefs.setLastActionAtMillis(now)
            prefs.setLastActionText("Scheduled cycle ran")

            val nextDelayMinutes = Random.nextLong(45L, 121L)
            prefs.setNextEligibleAtMillis(now + nextDelayMinutes * 60_000L)

            Result.success()
        } catch (exception: Exception) {
            prefs.incrementErrorCount()
            Result.retry()
        }
    }
}
