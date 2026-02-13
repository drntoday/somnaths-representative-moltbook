package com.somnath.representative.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.somnath.representative.ai.PhiInferenceEngine
import com.somnath.representative.ai.PhiRuntime
import com.somnath.representative.data.LastGeneratedCandidateStore
import com.somnath.representative.data.RssFeedConfigLoader
import com.somnath.representative.data.SchedulerPrefs
import com.somnath.representative.duplicate.GateStatus
import com.somnath.representative.duplicate.LocalTinyCacheGate
import com.somnath.representative.duplicate.TinyFingerprintCacheStore
import com.somnath.representative.factpack.FactPack
import com.somnath.representative.factpack.FactPackBuilder
import com.somnath.representative.rss.RssFetcher
import com.somnath.representative.safety.SafetyDecision
import com.somnath.representative.safety.SafetyGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DEFAULT_TOPIC = "Android background tasks"

class SomnathRepWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            SchedulerPrefs.updateLastActionMessage(applicationContext, "Generating...")

            val topic = SchedulerPrefs.getTopicQuery(applicationContext).ifBlank { DEFAULT_TOPIC }

            val rssFeeds = RssFeedConfigLoader().load(applicationContext)
            val rssItems = withContext(Dispatchers.IO) {
                rssFeeds.firstOrNull()?.let { feedUrl ->
                    RssFetcher().fetch(feedUrl, limit = 5).getOrElse { emptyList() }
                } ?: emptyList()
            }

            val factPack = FactPackBuilder().build(
                topic = topic,
                rssItems = rssItems,
                searchResults = emptyList()
            )

            val prompt = buildPrompt(topic, factPack)
            val generatedCandidate = PhiInferenceEngine(applicationContext).generate(prompt).trim()

            val safetyResult = SafetyGuard().evaluate(
                threadText = topic,
                draftText = generatedCandidate,
                factPack = factPack
            )

            if (safetyResult.decision == SafetyDecision.SKIP) {
                LastGeneratedCandidateStore.clear()
                val skipMessage = "Skipped: ${safetyResult.reason}"
                SchedulerPrefs.recordScheduledCycle(applicationContext, skipMessage)
                return Result.success()
            }

            val tinyCacheGate = LocalTinyCacheGate(
                cacheStore = TinyFingerprintCacheStore(applicationContext),
                phiRuntime = PhiRuntime(applicationContext)
            )
            val localGate = tinyCacheGate.evaluateCommentDraft(safetyResult.finalText)
            if (localGate.decision.status == GateStatus.SKIP) {
                LastGeneratedCandidateStore.clear()
                SchedulerPrefs.recordScheduledCycle(applicationContext, "Skipped duplicate")
                return Result.success()
            }

            LastGeneratedCandidateStore.set(
                candidateText = localGate.finalDraftText,
                candidateTopic = topic
            )
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

    private fun buildPrompt(topic: String, factPack: FactPack): String {
        val bullets = factPack.bullets.take(5).joinToString("\n") { "- $it" }
        return buildString {
            appendLine("Topic: $topic")
            if (bullets.isNotBlank()) {
                appendLine("FactPack:")
                appendLine(bullets)
            }
            append("Write a calm, helpful comment under 80 words. No links. No quotes.")
        }
    }
}
