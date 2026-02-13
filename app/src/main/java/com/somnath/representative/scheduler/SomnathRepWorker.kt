package com.somnath.representative.scheduler

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.somnath.representative.ai.ModelSessionManager
import com.somnath.representative.ai.PhiInferenceEngine
import com.somnath.representative.ai.PhiRuntime
import com.somnath.representative.data.ApiKeyStore
import com.somnath.representative.data.AutonomousPostRateLimiter
import com.somnath.representative.data.LastGeneratedCandidateStore
import com.somnath.representative.data.PromptStyleStatsStore
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
private const val GENERATION_COOLDOWN_MS = 30L * 60L * 1000L

class SomnathRepWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            if (shouldSkipForCooldown()) {
                SchedulerPrefs.recordScheduledCycle(applicationContext, "Skipped: cooldown")
                SchedulerPrefs.updateAdaptiveInterval(applicationContext, SchedulerPrefs.CycleOutcome.NO_CANDIDATE)
                refreshPeriodicSchedule()
                return Result.success()
            }

            if (shouldSkipForLowBattery()) {
                SchedulerPrefs.recordScheduledCycle(applicationContext, "Skipped: low battery")
                SchedulerPrefs.updateAdaptiveInterval(applicationContext, SchedulerPrefs.CycleOutcome.NO_CANDIDATE)
                refreshPeriodicSchedule()
                return Result.success()
            }

            if (isMemoryPressureHigh()) {
                ModelSessionManager.unloadNow()
                SchedulerPrefs.recordScheduledCycle(applicationContext, "Skipped: low memory")
                SchedulerPrefs.updateAdaptiveInterval(applicationContext, SchedulerPrefs.CycleOutcome.NO_CANDIDATE)
                refreshPeriodicSchedule()
                return Result.success()
            }

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

            val selectedStyle = PromptStyleStatsStore.selectStyle(applicationContext)
            PromptStyleStatsStore.recordStyleUsed(applicationContext, selectedStyle)
            val prompt = buildPrompt(style = selectedStyle, topic = topic, factPack = factPack)
            val generatedCandidate = withContext(Dispatchers.Default) {
                PhiInferenceEngine(applicationContext).generate(prompt).trim()
            }

            val safetyResult = SafetyGuard().evaluate(
                threadText = topic,
                draftText = generatedCandidate,
                factPack = factPack
            )

            if (safetyResult.decision == SafetyDecision.SKIP) {
                LastGeneratedCandidateStore.clear()
                PromptStyleStatsStore.applyScoreDelta(applicationContext, selectedStyle, delta = -2)
                TopicHistoryStore.applyScoreDelta(applicationContext, topic, delta = -2)
                SchedulerPrefs.recordAuditEvent(applicationContext, "SKIPPED_SAFETY", safetyResult.reason.take(80))
                SchedulerPrefs.recordScheduledCycle(applicationContext, "Skipped: ${safetyResult.reason}")
                SchedulerPrefs.updateAdaptiveInterval(applicationContext, SchedulerPrefs.CycleOutcome.SKIPPED_SAFETY)
                refreshPeriodicSchedule()
                return Result.success()
            }
            PromptStyleStatsStore.applyScoreDelta(applicationContext, selectedStyle, delta = 1)
            TopicHistoryStore.applyScoreDelta(applicationContext, topic, delta = 1)

            val tinyCacheGate = LocalTinyCacheGate(
                cacheStore = TinyFingerprintCacheStore(applicationContext),
                phiRuntime = PhiRuntime(applicationContext)
            )
            val localGate = tinyCacheGate.evaluateCommentDraft(safetyResult.finalText)
            if (localGate.decision.status == GateStatus.SKIP) {
                LastGeneratedCandidateStore.clear()
                PromptStyleStatsStore.applyScoreDelta(applicationContext, selectedStyle, delta = -1)
                TopicHistoryStore.applyScoreDelta(applicationContext, topic, delta = -1)
                SchedulerPrefs.recordAuditEvent(applicationContext, "SKIPPED_DUPLICATE", "Duplicate")
                SchedulerPrefs.recordScheduledCycle(applicationContext, "Skipped duplicate")
                SchedulerPrefs.updateAdaptiveInterval(applicationContext, SchedulerPrefs.CycleOutcome.NO_CANDIDATE)
                refreshPeriodicSchedule()
                return Result.success()
            }

            LastGeneratedCandidateStore.set(
                candidateText = localGate.finalDraftText,
                candidateTopic = topic,
                candidateStyle = selectedStyle
            )
            SchedulerPrefs.recordGenerationCompleted(applicationContext)
            SchedulerPrefs.recordAuditEvent(applicationContext, "GENERATED", "Draft generated")
            SchedulerPrefs.updateAdaptiveInterval(applicationContext, SchedulerPrefs.CycleOutcome.GENERATED)
            refreshPeriodicSchedule()

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
                SchedulerPrefs.updateAdaptiveInterval(applicationContext, SchedulerPrefs.CycleOutcome.SKIPPED_RATE_LIMIT)
                refreshPeriodicSchedule()
                return Result.success()
            }

            val postableTopic = TopicHistoryStore.choosePostableTopic(applicationContext, topic)
            if (postableTopic == null || postableTopic != topic) {
                SchedulerPrefs.recordAuditEvent(applicationContext, "SKIPPED_RATE_LIMIT", "Topic cooldown active")
                SchedulerPrefs.recordScheduledCycle(applicationContext, "Auto-post skipped: topic cooldown active")
                SchedulerPrefs.updateAdaptiveInterval(applicationContext, SchedulerPrefs.CycleOutcome.SKIPPED_RATE_LIMIT)
                refreshPeriodicSchedule()
                return Result.success()
            }

            if (!AutonomousPostRateLimiter.canPostNow(applicationContext)) {
                SchedulerPrefs.recordAuditEvent(applicationContext, "SKIPPED_RATE_LIMIT", "Rate limit")
                SchedulerPrefs.recordScheduledCycle(applicationContext, "Rate limit reached")
                SchedulerPrefs.updateAdaptiveInterval(applicationContext, SchedulerPrefs.CycleOutcome.SKIPPED_RATE_LIMIT)
                refreshPeriodicSchedule()
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
                    TopicHistoryStore.recordTopicPosted(applicationContext, topic)
                    PromptStyleStatsStore.applyScoreDelta(applicationContext, selectedStyle, delta = 2)
                    TopicHistoryStore.applyScoreDelta(applicationContext, topic, delta = 2)
                    SchedulerPrefs.recordAuditEvent(applicationContext, "AUTO_POST_SUCCESS", "Posted")
                    SchedulerPrefs.recordScheduledCycle(applicationContext, "Auto-posted successfully")
                    SchedulerPrefs.updateAdaptiveInterval(applicationContext, SchedulerPrefs.CycleOutcome.POST_SUCCESS)
                },
                onFailure = {
                    SchedulerPrefs.recordAutoPostFailure(applicationContext)
                    PromptStyleStatsStore.applyScoreDelta(applicationContext, selectedStyle, delta = -3)
                    TopicHistoryStore.applyScoreDelta(applicationContext, topic, delta = -3)
                    SchedulerPrefs.recordAuditEvent(applicationContext, "AUTO_POST_FAIL", (it.message ?: "Auto-post failed").take(80))
                    SchedulerPrefs.recordScheduledCycle(applicationContext, "Auto-post failed")
                }
            )

            refreshPeriodicSchedule()

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

    private fun refreshPeriodicSchedule() {
        if (!SchedulerPrefs.isSchedulerEnabled(applicationContext)) {
            return
        }

        if (SchedulerPrefs.isAutonomousModeEnabled(applicationContext)) {
            SomnathRepScheduler.rescheduleWithAdaptiveDelay(applicationContext)
        } else {
            SomnathRepScheduler.schedule(
                context = applicationContext,
                chargingOnly = SchedulerPrefs.isChargingOnly(applicationContext),
                wifiOnly = SchedulerPrefs.isWifiOnly(applicationContext)
            )
        }
    }

    private fun shouldSkipForCooldown(now: Long = System.currentTimeMillis()): Boolean {
        if (SchedulerPrefs.isAutonomousModeEnabled(applicationContext)) return false
        if (inputData.getBoolean(KEY_MANUAL_ACTION_TRIGGERED, false)) return false
        val lastGenerationAt = SchedulerPrefs.getLastGenerationAt(applicationContext)
        return lastGenerationAt > 0L && now - lastGenerationAt < GENERATION_COOLDOWN_MS
    }

    private fun shouldSkipForLowBattery(): Boolean {
        val batteryStatus = applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        if (level <= 0 || scale <= 0) return false
        val batteryPercent = (level * 100f) / scale.toFloat()
        return batteryPercent < 20f && !isCharging
    }

    private fun isMemoryPressureHigh(): Boolean {
        val runtime = Runtime.getRuntime()
        return runtime.freeMemory().toDouble() < runtime.maxMemory().toDouble() * 0.10
    }

    private fun buildPrompt(style: PromptStyle, topic: String, factPack: FactPack?): String {
        val factBullets = factPack?.bullets.orEmpty().take(3)
        val styleInstruction = when (style) {
            PromptStyle.FRIENDLY -> "Write a warm, supportive reply in 60-90 words. Include at most one question."
            PromptStyle.ANALYTICAL -> "Write a structured reply in 60-90 words with at most two short bullets."
            PromptStyle.MINIMAL -> "Write a single-paragraph reply in 40-60 words."
            PromptStyle.INSIGHTFUL -> "Write a 60-90 word reply with one memorable insight and no bullets."
        }

        return buildString {
            appendLine("Style: ${style.name}")
            appendLine("Topic: $topic")
            if (factBullets.isNotEmpty()) {
                appendLine("FactPack (up to 3 points):")
                factBullets.forEach { appendLine("- $it") }
            }
            appendLine("No links. No quotes. Calm tone.")
            append(styleInstruction)
        }
    }
}

private const val KEY_MANUAL_ACTION_TRIGGERED = "manualActionTriggered"
