package com.somnath.representative.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.somnath.representative.ai.PhiInferenceEngine
import com.somnath.representative.ai.PhiRuntime
import com.somnath.representative.data.ApiKeyStore
import com.somnath.representative.data.AutonomousPostRateLimiter
import com.somnath.representative.data.LastGeneratedCandidateStore
import com.somnath.representative.data.RssFeedConfigLoader
import com.somnath.representative.data.SchedulerPrefs
import com.somnath.representative.data.SubmoltConfigLoader
import com.somnath.representative.data.TopicHistoryStore
import com.somnath.representative.duplicate.GateStatus
import com.somnath.representative.duplicate.LocalTinyCacheGate
import com.somnath.representative.duplicate.TinyFingerprintCacheStore
import com.somnath.representative.factpack.FactPack
import com.somnath.representative.factpack.FactPackBuilder
import com.somnath.representative.moltbook.OkHttpMoltbookApi
import com.somnath.representative.rss.RssFetcher
import com.somnath.representative.safety.SafetyDecision
import com.somnath.representative.safety.SafetyGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DEFAULT_TOPIC = "Android background tasks"
private const val AUTO_POST_CONFIDENCE_THRESHOLD = 80

class SomnathRepWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            SchedulerPrefs.updateLastActionMessage(applicationContext, "Generating")
            SchedulerPrefs.recordAuditEvent(applicationContext, "WORKER_RAN", "Worker ran")

            val baseTopic = SchedulerPrefs.getTopicQuery(applicationContext).ifBlank { DEFAULT_TOPIC }
            val configuredSubmolts = SubmoltConfigLoader().load(applicationContext)
            val topic = TopicHistoryStore.selectAdaptiveTopic(
                context = applicationContext,
                defaultTopic = baseTopic,
                explorationPool = configuredSubmolts
            )
            TopicHistoryStore.recordTopicUsed(applicationContext, topic)

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
                TopicHistoryStore.applyScoreDelta(applicationContext, topic, delta = -2)
                SchedulerPrefs.recordAuditEvent(applicationContext, "SKIPPED_SAFETY", safetyResult.reason.take(80))
                SchedulerPrefs.recordScheduledCycle(applicationContext, "Skipped: ${safetyResult.reason}")
                return Result.success()
            }
            TopicHistoryStore.applyScoreDelta(applicationContext, topic, delta = 1)

            val tinyCacheGate = LocalTinyCacheGate(
                cacheStore = TinyFingerprintCacheStore(applicationContext),
                phiRuntime = PhiRuntime(applicationContext)
            )
            val localGate = tinyCacheGate.evaluateCommentDraft(safetyResult.finalText)
            if (localGate.decision.status == GateStatus.SKIP) {
                LastGeneratedCandidateStore.clear()
                TopicHistoryStore.applyScoreDelta(applicationContext, topic, delta = -1)
                SchedulerPrefs.recordAuditEvent(applicationContext, "SKIPPED_DUPLICATE", "Duplicate")
                SchedulerPrefs.recordScheduledCycle(applicationContext, "Skipped duplicate")
                return Result.success()
            }

            LastGeneratedCandidateStore.set(
                candidateText = localGate.finalDraftText,
                candidateTopic = topic
            )
            SchedulerPrefs.recordAuditEvent(applicationContext, "GENERATED", "Draft generated")

            if (SchedulerPrefs.isEmergencyStopEnabled(applicationContext)) {
                SchedulerPrefs.recordAuditEvent(applicationContext, "SKIPPED_SAFETY", "Emergency stop")
                SchedulerPrefs.recordScheduledCycle(applicationContext, "Posting disabled by Emergency Stop")
                return Result.success()
            }

            if (!SchedulerPrefs.isAutonomousModeEnabled(applicationContext)) {
                SchedulerPrefs.recordScheduledCycle(applicationContext, "Generated candidate successfully")
                return Result.success()
            }

            if (safetyResult.confidence < AUTO_POST_CONFIDENCE_THRESHOLD) {
                SchedulerPrefs.recordScheduledCycle(applicationContext, "Auto-post skipped: confidence too low")
                return Result.success()
            }

            if (SchedulerPrefs.isAutoPostInCooldown(applicationContext)) {
                SchedulerPrefs.recordAuditEvent(applicationContext, "SKIPPED_RATE_LIMIT", "Cooldown")
                SchedulerPrefs.recordScheduledCycle(applicationContext, "Auto-post cooldown active")
                return Result.success()
            }

            if (!AutonomousPostRateLimiter.canPostNow(applicationContext)) {
                SchedulerPrefs.recordAuditEvent(applicationContext, "SKIPPED_RATE_LIMIT", "Rate limit")
                SchedulerPrefs.recordScheduledCycle(applicationContext, "Rate limit reached")
                return Result.success()
            }

            if (configuredSubmolts.isEmpty()) {
                SchedulerPrefs.recordScheduledCycle(applicationContext, "Auto-post skipped: no submolts")
                return Result.success()
            }

            val apiStore = ApiKeyStore(applicationContext)
            val moltbookApi = OkHttpMoltbookApi(apiKeyProvider = { apiStore.getApiKey() })
            val targetPost = runCatching {
                moltbookApi.fetchFeed(configuredSubmolts, limit = 1).firstOrNull()
            }.getOrNull()

            if (targetPost == null) {
                SchedulerPrefs.recordScheduledCycle(applicationContext, "Auto-post skipped: no target post")
                return Result.success()
            }

            val postResult = moltbookApi.postComment(postId = targetPost.id, body = localGate.finalDraftText)
            postResult.fold(
                onSuccess = {
                    tinyCacheGate.registerPostedFingerprint(localGate.finalFingerprint, type = "comment")
                    AutonomousPostRateLimiter.recordSuccessfulPost(applicationContext)
                    SchedulerPrefs.recordAutoPostSuccess(applicationContext)
                    TopicHistoryStore.applyScoreDelta(applicationContext, topic, delta = 2)
                    SchedulerPrefs.recordAuditEvent(applicationContext, "AUTO_POST_SUCCESS", "Posted")
                    SchedulerPrefs.recordScheduledCycle(applicationContext, "Auto-posted successfully")
                },
                onFailure = {
                    SchedulerPrefs.recordAutoPostFailure(applicationContext)
                    TopicHistoryStore.applyScoreDelta(applicationContext, topic, delta = -3)
                    SchedulerPrefs.recordAuditEvent(applicationContext, "AUTO_POST_FAIL", (it.message ?: "Auto-post failed").take(80))
                    SchedulerPrefs.recordScheduledCycle(applicationContext, "Auto-post failed")
                }
            )

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
