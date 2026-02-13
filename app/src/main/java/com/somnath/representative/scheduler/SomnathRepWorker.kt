package com.somnath.representative.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.somnath.representative.ai.PhiInferenceEngine
import com.somnath.representative.data.LastGeneratedCandidateStore
import com.somnath.representative.data.SchedulerPrefs
import com.somnath.representative.data.SubmoltConfigLoader

class SomnathRepWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            SchedulerPrefs.updateLastActionMessage(applicationContext, "Generating...")

            val submolts = SubmoltConfigLoader().load(applicationContext)
            val prompt = "Submolts: ${submolts.joinToString(", ")}\nGenerate one thoughtful, respectful comment under 60 words."
            val generatedCandidate = PhiInferenceEngine(applicationContext).generate(prompt)
            LastGeneratedCandidateStore.set(generatedCandidate)

            SchedulerPrefs.recordScheduledCycle(applicationContext, "Generated candidate successfully")
            Result.success()
        } catch (e: Exception) {
            SchedulerPrefs.incrementErrors(applicationContext)
            SchedulerPrefs.updateLastActionMessage(
                applicationContext,
                e.message ?: "Generation failed"
            )
            Result.retry()
        }
    }
}
