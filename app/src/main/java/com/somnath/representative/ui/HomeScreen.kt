package com.somnath.representative.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.somnath.representative.BuildConfig
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
import com.somnath.representative.duplicate.StubSelfHistoryGate
import com.somnath.representative.duplicate.StubThreadDuplicationGate
import com.somnath.representative.duplicate.TinyFingerprintCacheStore
import com.somnath.representative.factpack.FactPack
import com.somnath.representative.factpack.FactPackBuilder
import com.somnath.representative.ai.PhiModelManager
import com.somnath.representative.ai.PhiRuntime
import com.somnath.representative.moltbook.OkHttpMoltbookApi
import com.somnath.representative.moltbook.PostSummary
import com.somnath.representative.rss.RssFetcher
import com.somnath.representative.rss.RssItem
import com.somnath.representative.scheduler.PromptStyle
import com.somnath.representative.safety.SafetyDecision
import com.somnath.representative.safety.SafetyGuard
import com.somnath.representative.search.SearchProviderConfigLoader
import com.somnath.representative.search.StubSearchVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

@Composable
fun HomeScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val homeStatus = remember { mutableStateOf(SchedulerPrefs.getHomeStatus(context)) }
    val autonomousModeEnabled = remember { mutableStateOf(SchedulerPrefs.isAutonomousModeEnabled(context)) }
    val emergencyStopEnabled = remember { mutableStateOf(SchedulerPrefs.isEmergencyStopEnabled(context)) }
    val autonomousRateStatus = remember { mutableStateOf(AutonomousPostRateLimiter.getStatus(context)) }
    val scheduleMode = remember { mutableStateOf(SchedulerPrefs.getScheduleMode(context)) }
    val nextRunDelayMinutes = remember { mutableStateOf(SchedulerPrefs.getDisplayedNextRunDelayMinutes(context)) }
    val nextAutoPostAllowedAt = remember { mutableStateOf(SchedulerPrefs.getNextAutoPostAllowedAt(context)) }
    val auditEvents = remember { mutableStateOf(SchedulerPrefs.getRecentAuditEvents(context, limit = 3)) }
    val lastGeneratedCandidate = remember { mutableStateOf(LastGeneratedCandidateStore.get()) }
    val adaptiveTopicStats = remember { mutableStateOf(TopicHistoryStore.getAdaptiveStats(context)) }
    val promptStyleScores = remember { mutableStateOf(PromptStyleStatsStore.getScoreMap(context)) }
    val topPromptStyle = remember { mutableStateOf(PromptStyleStatsStore.getTopStyle(context)) }
    val submolts = remember { SubmoltConfigLoader().load(context) }
    val rssFeedLoader = remember { RssFeedConfigLoader() }
    val searchProviderConfigLoader = remember { SearchProviderConfigLoader() }
    val apiKeyStore = remember { ApiKeyStore(context) }
    val moltbookApi = remember { OkHttpMoltbookApi(apiKeyProvider = { apiKeyStore.getApiKey() }) }
    val phiModelManager = remember { PhiModelManager(context) }
    val phiRuntime = remember { PhiRuntime(context) }
    val tinyCacheStore = remember { TinyFingerprintCacheStore(context) }
    val tinyCacheGate = remember { LocalTinyCacheGate(tinyCacheStore, phiRuntime) }
    val selfHistoryGate = remember { StubSelfHistoryGate() }
    val threadDuplicationGate = remember { StubThreadDuplicationGate() }
    val rssFetcher = remember { RssFetcher() }
    val searchVerifier = remember { StubSearchVerifier() }
    val factPackBuilder = remember { FactPackBuilder() }
    val safetyGuard = remember { SafetyGuard() }

    var fetchedPosts by remember { mutableStateOf<List<PostSummary>>(emptyList()) }
    var m3Status by remember { mutableStateOf("Idle") }

    val latestCandidateText = lastGeneratedCandidate.value?.candidateText.orEmpty()
    val latestCandidateTopic = lastGeneratedCandidate.value?.candidateTopic.orEmpty()
    var m4Prompt by remember {
        mutableStateOf("Write a calm 2-sentence reply about building Android apps.")
    }
    var m4Status by remember { mutableStateOf("Idle") }
    var m4Confidence by remember { mutableStateOf<Int?>(null) }
    var m4Decision by remember { mutableStateOf<String?>(null) }

    var m5Topic by remember {
        mutableStateOf(SchedulerPrefs.getTopicQuery(context).ifBlank { "Latest Android AI release" })
    }
    var m5Status by remember { mutableStateOf("Idle") }
    var m5FactPack by remember { mutableStateOf<FactPack?>(null) }
    var tinyCacheCount by remember { mutableStateOf(tinyCacheStore.getRecentFingerprints().size) }
    var pendingManualPostDraft by remember { mutableStateOf<String?>(null) }
    var pendingManualPostId by remember { mutableStateOf<String?>(null) }

    fun refreshStatus() {
        homeStatus.value = SchedulerPrefs.getHomeStatus(context)
        autonomousModeEnabled.value = SchedulerPrefs.isAutonomousModeEnabled(context)
        emergencyStopEnabled.value = SchedulerPrefs.isEmergencyStopEnabled(context)
        autonomousRateStatus.value = AutonomousPostRateLimiter.getStatus(context)
        scheduleMode.value = SchedulerPrefs.getScheduleMode(context)
        nextRunDelayMinutes.value = SchedulerPrefs.getDisplayedNextRunDelayMinutes(context)
        nextAutoPostAllowedAt.value = SchedulerPrefs.getNextAutoPostAllowedAt(context)
        auditEvents.value = SchedulerPrefs.getRecentAuditEvents(context, limit = 3)
        lastGeneratedCandidate.value = LastGeneratedCandidateStore.get()
        adaptiveTopicStats.value = TopicHistoryStore.getAdaptiveStats(context)
        promptStyleScores.value = PromptStyleStatsStore.getScoreMap(context)
        topPromptStyle.value = PromptStyleStatsStore.getTopStyle(context)
    }

    LaunchedEffect(Unit) {
        refreshStatus()
        phiModelManager.maybeAutoDownloadOnFirstOpen()
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val lastAction = if (homeStatus.value.lastActionTime > 0L) {
        DateFormat.format("yyyy-MM-dd HH:mm", Date(homeStatus.value.lastActionTime)).toString()
    } else {
        "—"
    }

    val hasSubmolts = submolts.isNotEmpty()
    val debugToolsEnabled = BuildConfig.DEBUG && SchedulerPrefs.isDebugToolsEnabled(context)
    val nextTopicCandidate = TopicHistoryStore.selectAdaptiveTopic(
        context = context,
        defaultTopic = SchedulerPrefs.getTopicQuery(context).ifBlank { "latest android ai release" },
        explorationPool = submolts
    )
    val topicCooldownActive = TopicHistoryStore.isTopicPostCooldownActive(context, nextTopicCandidate)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Somnath’s Representative",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Scheduler: ${if (homeStatus.value.schedulerEnabled) "Enabled" else "Disabled"}",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Schedule mode: ${if (scheduleMode.value == SchedulerPrefs.SCHEDULE_MODE_ADAPTIVE_ONE_TIME_CHAIN) "Adaptive" else "Periodic"}",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Next run delay: ${nextRunDelayMinutes.value}m",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Last action: $lastAction",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Next topic candidate: $nextTopicCandidate",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Topic cooldown active: ${if (topicCooldownActive) "Yes" else "No"}",
            style = MaterialTheme.typography.bodyLarge
        )
        if (homeStatus.value.lastActionMessage.isNotBlank()) {
            Text(
                text = "Status: ${homeStatus.value.lastActionMessage}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = "Actions today: ${homeStatus.value.actionsTodayCount}",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(text = "Errors: ${homeStatus.value.errorsCount}", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "Autonomous Mode: ${if (autonomousModeEnabled.value) "ON" else "OFF"}",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Emergency Stop: ${if (emergencyStopEnabled.value) "ON" else "OFF"}",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Posts last 24h: ${autonomousRateStatus.value.postsLast24h}",
            style = MaterialTheme.typography.bodyLarge
        )
        val nextAllowedPost = autonomousRateStatus.value.nextAllowedPostAt
        val nextAllowedLabel = if (autonomousRateStatus.value.canPostNow || nextAllowedPost <= 0L) {
            "Ready"
        } else {
            DateFormat.format("yyyy-MM-dd HH:mm", Date(nextAllowedPost)).toString()
        }
        Text(
            text = "Next allowed post: $nextAllowedLabel",
            style = MaterialTheme.typography.bodyLarge
        )
        val cooldownLabel = if (nextAutoPostAllowedAt.value > System.currentTimeMillis()) {
            DateFormat.format("yyyy-MM-dd HH:mm", Date(nextAutoPostAllowedAt.value)).toString()
        } else {
            "Ready"
        }
        Text(
            text = "Cooldown until: $cooldownLabel",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Last pipeline: RSS+FactPack -> Safety -> DupGate -> Generated/Skipped",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Adaptive Topic Stats",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        val topTopicLabel = adaptiveTopicStats.value.topTopic?.let { "${it.topic} (${it.score})" } ?: "—"
        val lastTopicLabel = adaptiveTopicStats.value.lastTopicUsed?.topic ?: "—"
        Text(
            text = "Top topic: $topTopicLabel",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Total tracked topics: ${adaptiveTopicStats.value.totalTrackedTopics}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Last topic used: $lastTopicLabel",
            style = MaterialTheme.typography.bodyMedium
        )
        val candidateStyleLabel = lastGeneratedCandidate.value?.candidateStyle?.name ?: "—"
        val topStyleLabel = topPromptStyle.value?.let { "${it.style.name} (${it.score})" } ?: "—"
        val scoreMap = promptStyleScores.value
        Text(
            text = "Prompt style used: $candidateStyleLabel",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Top style: $topStyleLabel",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Style stats: F=${scoreMap[PromptStyle.FRIENDLY] ?: 0} A=${scoreMap[PromptStyle.ANALYTICAL] ?: 0} M=${scoreMap[PromptStyle.MINIMAL] ?: 0} I=${scoreMap[PromptStyle.INSIGHTFUL] ?: 0}",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Last Generated Candidate",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = latestCandidateText
                .take(200)
                .ifBlank { "No generation yet." },
            style = MaterialTheme.typography.bodyMedium
        )
        if (latestCandidateTopic.isNotBlank()) {
            Text(
                text = "Topic: $latestCandidateTopic",
                style = MaterialTheme.typography.bodySmall
            )
        }
        lastGeneratedCandidate.value?.generatedAt?.let { generatedAt ->
            val generatedAtLabel = DateFormat.format("yyyy-MM-dd HH:mm", Date(generatedAt)).toString()
            Text(
                text = "Generated at: $generatedAtLabel",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            text = if (emergencyStopEnabled.value) {
                "Posting disabled by Emergency Stop"
            } else if (autonomousModeEnabled.value) {
                "Safe autonomous mode enabled. Auto-post guarded by limits."
            } else {
                "Manual only. Will not auto-post."
            },
            style = MaterialTheme.typography.bodySmall
        )

        Text(
            text = "Recent audit events",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        auditEvents.value.forEach { event ->
            val timestampLabel = DateFormat.format("MM-dd HH:mm", Date(event.timestamp)).toString()
            Text(
                text = "$timestampLabel • ${event.shortMessage}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Button(
            onClick = {
                coroutineScope.launch {
                    if (SchedulerPrefs.isEmergencyStopEnabled(context)) {
                        SchedulerPrefs.recordAuditEvent(context, "MANUAL_POST_BLOCKED_STOP", "Emergency stop")
                        m3Status = "Posting disabled by Emergency Stop"
                        refreshStatus()
                        return@launch
                    }

                    val apiKey = apiKeyStore.getApiKey()
                    if (apiKey.isNullOrBlank()) {
                        m3Status = "Set API key in Settings"
                        return@launch
                    }

                    val candidateSnapshot = lastGeneratedCandidate.value
                    if (candidateSnapshot == null || candidateSnapshot.candidateText.isBlank()) {
                        m3Status = "No candidate yet"
                        return@launch
                    }

                    if (TopicHistoryStore.isTopicPostCooldownActive(context, candidateSnapshot.candidateTopic)) {
                        m3Status = "Skipped: topic cooldown active"
                        return@launch
                    }

                    if (!hasSubmolts) {
                        m3Status = "No submolts configured"
                        return@launch
                    }

                    var feedFetchFailed = false
                    val targetPost = fetchedPosts.firstOrNull() ?: runCatching {
                        moltbookApi.fetchFeed(submolts, limit = 1)
                    }.fold(
                        onSuccess = { posts ->
                            if (posts.isNotEmpty()) {
                                fetchedPosts = posts
                            }
                            posts.firstOrNull()
                        },
                        onFailure = {
                            feedFetchFailed = true
                            m3Status = it.message ?: "Fetch failed"
                            null
                        }
                    )

                    if (targetPost == null) {
                        if (!feedFetchFailed) {
                            m3Status = "No posts available to comment on."
                        }
                        return@launch
                    }

                    val safetyResult = safetyGuard.evaluate(
                        threadText = candidateSnapshot.candidateTopic,
                        draftText = candidateSnapshot.candidateText,
                        factPack = null
                    )

                    if (safetyResult.decision == SafetyDecision.SKIP || safetyResult.decision == SafetyDecision.ASK_QUESTION) {
                        PromptStyleStatsStore.applyScoreDelta(context, candidateSnapshot.candidateStyle, delta = -2)
                        m3Status = "Skipped: ${safetyResult.reason}"
                        refreshStatus()
                        return@launch
                    }
                    PromptStyleStatsStore.applyScoreDelta(context, candidateSnapshot.candidateStyle, delta = 1)

                    val localGate = tinyCacheGate.evaluateCommentDraft(safetyResult.finalText)
                    if (localGate.decision.status == GateStatus.SKIP) {
                        PromptStyleStatsStore.applyScoreDelta(context, candidateSnapshot.candidateStyle, delta = -1)
                        m3Status = "Skipped duplicate"
                        refreshStatus()
                        return@launch
                    }

                    val result = moltbookApi.postComment(postId = targetPost.id, body = localGate.finalDraftText)
                    m3Status = result.fold(
                        onSuccess = {
                            tinyCacheGate.registerPostedFingerprint(localGate.finalFingerprint, type = "comment")
                            tinyCacheCount = tinyCacheStore.getRecentFingerprints().size
                            TopicHistoryStore.recordTopicPosted(context, candidateSnapshot.candidateTopic)
                            PromptStyleStatsStore.applyScoreDelta(context, candidateSnapshot.candidateStyle, delta = 2)
                            TopicHistoryStore.applyScoreDelta(context, candidateSnapshot.candidateTopic, delta = 2)
                            SchedulerPrefs.recordAuditEvent(context, "MANUAL_POST_SUCCESS", "Posted")
                            refreshStatus()
                            "Posted successfully"
                        },
                        onFailure = {
                            PromptStyleStatsStore.applyScoreDelta(context, candidateSnapshot.candidateStyle, delta = -3)
                            TopicHistoryStore.applyScoreDelta(context, candidateSnapshot.candidateTopic, delta = -3)
                            refreshStatus()
                            it.message ?: "Post failed"
                        }
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Post Candidate to Moltbook (Manual)")
        }
        if (debugToolsEnabled) {
            Button(
                onClick = {
                    val constraintsBuilder = Constraints.Builder()
                        .setRequiredNetworkType(
                            if (SchedulerPrefs.isWifiOnly(context)) {
                                NetworkType.UNMETERED
                            } else {
                                NetworkType.CONNECTED
                            }
                        )
                    if (SchedulerPrefs.isChargingOnly(context)) {
                        constraintsBuilder.setRequiresCharging(true)
                    }
                    val request = OneTimeWorkRequestBuilder<com.somnath.representative.scheduler.SomnathRepWorker>()
                        .setInputData(workDataOf("manualActionTriggered" to true))
                        .setConstraints(constraintsBuilder.build())
                        .build()
                    WorkManager.getInstance(context).enqueue(request)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Run Now (Debug Tools)")
            }
        }
        Text(text = "Tiny cache entries: $tinyCacheCount/20", style = MaterialTheme.typography.bodyLarge)
        Button(
            onClick = {
                tinyCacheStore.clear()
                tinyCacheCount = tinyCacheStore.getRecentFingerprints().size
                m3Status = "Tiny cache cleared"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Clear Tiny Cache")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "M3 Test", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (!hasSubmolts) {
            Text(text = "No submolts configured", style = MaterialTheme.typography.bodyMedium)
        }

        Button(
            onClick = {
                coroutineScope.launch {
                    runCatching {
                        m3Status = "Fetching feed..."
                        val posts = moltbookApi.fetchFeed(submolts, limit = 10)
                        fetchedPosts = posts
                        val firstTitle = posts.firstOrNull()?.title ?: "(no title)"
                        m3Status = "Fetch succeeded: ${posts.size} posts. First title: $firstTitle"
                    }.onFailure {
                        m3Status = it.message ?: "Fetch failed"
                    }
                }
            },
            enabled = hasSubmolts,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Test: Fetch Feed")
        }

        Button(
            onClick = {
                coroutineScope.launch {
                    if (SchedulerPrefs.isEmergencyStopEnabled(context)) {
                        SchedulerPrefs.recordAuditEvent(context, "MANUAL_POST_BLOCKED_STOP", "Emergency stop")
                        m3Status = "Posting disabled by Emergency Stop"
                        refreshStatus()
                        return@launch
                    }

                    if (fetchedPosts.isEmpty()) {
                        m3Status = "Fetch feed first so a post is available for commenting."
                        return@launch
                    }

                    val targetPostId = fetchedPosts.first().id
                    val originalDraft = "Test comment from Somnath’s Representative (M3)"
                    val localGate = tinyCacheGate.evaluateCommentDraft(originalDraft)
                    if (localGate.decision.status == GateStatus.SKIP) {
                        m3Status = "Skipped due to duplicate"
                        return@launch
                    }

                    val statusParts = mutableListOf(localGate.decision.message)

                    val selfHistoryDecision = selfHistoryGate.check(localGate.finalDraftText).getOrElse {
                        statusParts.add(it.message ?: "Self-history gate failed; allowing by default")
                        com.somnath.representative.duplicate.GateDecision(
                            status = GateStatus.UNKNOWN,
                            message = "Self-history gate failed; allowing by default"
                        )
                    }
                    if (selfHistoryDecision.status == GateStatus.SKIP) {
                        m3Status = "Skipped due to duplicate"
                        return@launch
                    }
                    if (selfHistoryDecision.status == GateStatus.UNKNOWN) {
                        statusParts.add(selfHistoryDecision.message)
                    }

                    val threadDecision = threadDuplicationGate.check(
                        localGate.finalDraftText,
                        threadComments = emptyList()
                    ).getOrElse {
                        statusParts.add(it.message ?: "Thread-duplication gate failed; allowing by default")
                        com.somnath.representative.duplicate.GateDecision(
                            status = GateStatus.UNKNOWN,
                            message = "Thread-duplication gate failed; allowing by default"
                        )
                    }
                    if (threadDecision.status == GateStatus.SKIP) {
                        m3Status = "Skipped due to duplicate"
                        return@launch
                    }
                    if (threadDecision.status == GateStatus.UNKNOWN) {
                        statusParts.add(threadDecision.message)
                    }

                    val threadText = buildString {
                        append(fetchedPosts.first().title.orEmpty())
                        append('\n')
                        append(fetchedPosts.first().body.orEmpty())
                    }
                    val safetyResult = safetyGuard.evaluate(
                        threadText = threadText,
                        draftText = localGate.finalDraftText,
                        factPack = null
                    )
                    statusParts.add("Safety=${safetyResult.decision} confidence=${safetyResult.confidence}")

                    if (safetyResult.decision == SafetyDecision.SKIP) {
                        m3Status = "Skipped: ${safetyResult.reason} (${statusParts.joinToString(" | ")})"
                        pendingManualPostDraft = null
                        pendingManualPostId = null
                        return@launch
                    }

                    if (safetyResult.decision == SafetyDecision.ASK_QUESTION) {
                        pendingManualPostDraft = safetyResult.finalText
                        pendingManualPostId = targetPostId
                        m3Status = "Ask-question mode: ${safetyResult.reason}. Press Post Anyway to post neutral question."
                        return@launch
                    }

                    val result = moltbookApi.postComment(postId = targetPostId, body = safetyResult.finalText)
                    m3Status = result.fold(
                        onSuccess = {
                            tinyCacheGate.registerPostedFingerprint(localGate.finalFingerprint, type = "comment")
                            tinyCacheCount = tinyCacheStore.getRecentFingerprints().size
                            TopicHistoryStore.applyScoreDelta(context, "M3 Test comment", delta = 2)
                            val gateSuffix = if (statusParts.isNotEmpty()) {
                                " (${statusParts.joinToString(" | ")})"
                            } else {
                                ""
                            }
                            SchedulerPrefs.recordAuditEvent(context, "MANUAL_POST_SUCCESS", "Posted")
                            refreshStatus()
                            "Post comment succeeded on post: $targetPostId$gateSuffix"
                        },
                        onFailure = {
                            TopicHistoryStore.applyScoreDelta(context, "M3 Test comment", delta = -3)
                            refreshStatus()
                            it.message ?: "Post comment failed"
                        }
                    )
                }
            },
            enabled = hasSubmolts,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Test: Post Comment")
        }

        Button(
            onClick = {
                coroutineScope.launch {
                    if (SchedulerPrefs.isEmergencyStopEnabled(context)) {
                        SchedulerPrefs.recordAuditEvent(context, "MANUAL_POST_BLOCKED_STOP", "Emergency stop")
                        m3Status = "Posting disabled by Emergency Stop"
                        refreshStatus()
                        return@launch
                    }

                    val postId = pendingManualPostId
                    val draft = pendingManualPostDraft
                    if (postId.isNullOrBlank() || draft.isNullOrBlank()) {
                        m3Status = "No pending neutral question to post."
                        return@launch
                    }

                    val result = moltbookApi.postComment(postId = postId, body = draft)
                    m3Status = result.fold(
                        onSuccess = {
                            pendingManualPostDraft = null
                            pendingManualPostId = null
                            TopicHistoryStore.applyScoreDelta(context, "M3 Ask Question", delta = 2)
                            SchedulerPrefs.recordAuditEvent(context, "MANUAL_POST_SUCCESS", "Posted")
                            refreshStatus()
                            "Post Anyway succeeded on post: $postId"
                        },
                        onFailure = {
                            TopicHistoryStore.applyScoreDelta(context, "M3 Ask Question", delta = -3)
                            refreshStatus()
                            it.message ?: "Post Anyway failed"
                        }
                    )
                }
            },
            enabled = !pendingManualPostDraft.isNullOrBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Post Anyway")
        }

        Text(text = "M3 status: $m3Status", style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "M4 Test", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        TextField(
            value = m4Prompt,
            onValueChange = { m4Prompt = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Prompt") }
        )

        Button(
            onClick = {
                coroutineScope.launch {
                    val generationOutput = withContext(Dispatchers.Default) {
                        phiRuntime.generate(prompt = m4Prompt)
                    }
                    val safetyResult = safetyGuard.evaluate(
                        threadText = m4Prompt,
                        draftText = clampDisplayWordCount(generationOutput),
                        factPack = m5FactPack
                    )
                    m4Confidence = safetyResult.confidence
                    m4Decision = "${safetyResult.decision} (${safetyResult.reason})"
                    m4Status = if (safetyResult.decision == SafetyDecision.SKIP) {
                        "SKIP: ${safetyResult.reason}"
                    } else {
                        safetyResult.finalText
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Test: Generate (Phi)")
        }
        m4Confidence?.let {
            Text(text = "M4 confidence: $it", style = MaterialTheme.typography.bodyMedium)
        }
        m4Decision?.let {
            Text(text = "M4 safety: $it", style = MaterialTheme.typography.bodyMedium)
        }
        Text(text = "M4 output: $m4Status", style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "M5 Test", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        TextField(
            value = m5Topic,
            onValueChange = {
                m5Topic = it
                SchedulerPrefs.setTopicQuery(context, it)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Topic / Query") }
        )

        Button(
            onClick = {
                coroutineScope.launch {
                    m5Status = "Loading RSS feeds..."
                    m5FactPack = null

                    val feeds = rssFeedLoader.load(context)
                    if (feeds.isEmpty()) {
                        m5Status = "No RSS feeds configured in rss_feeds.json"
                    }

                    val rssResult = withContext(Dispatchers.IO) {
                        fetchRssSignals(rssFetcher, feeds, maxFeeds = 2, itemLimit = 3)
                    }

                    val searchProvider = searchProviderConfigLoader.load(context)
                    val searchResult = searchVerifier.search(m5Topic, limit = 5)
                    val searchItems = searchResult.getOrElse { emptyList() }

                    val factPack = factPackBuilder.build(
                        topic = m5Topic,
                        rssItems = rssResult.items,
                        searchResults = searchItems
                    )
                    m5FactPack = factPack

                    val statusParts = mutableListOf<String>()
                    statusParts.add("RSS items: ${rssResult.items.size}")
                    if (rssResult.errors.isNotEmpty()) {
                        statusParts.add("RSS errors: ${rssResult.errors.joinToString(" | ")}")
                    }
                    statusParts.add(
                        searchResult.fold(
                            onSuccess = { "Search results: ${it.size}" },
                            onFailure = {
                                "Search: ${it.message ?: "not configured"} (provider=${searchProvider.provider})"
                            }
                        )
                    )
                    m5Status = statusParts.joinToString(" • ")
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Test: Build Fact Pack")
        }

        Text(text = "M5 status: $m5Status", style = MaterialTheme.typography.bodyMedium)
        m5FactPack?.let { factPack ->
            Text(
                text = "Confidence: ${factPack.confidence}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(text = "As-of: ${factPack.asOf}", style = MaterialTheme.typography.bodyLarge)
            factPack.bullets.forEachIndexed { index, bullet ->
                Text(text = "${index + 1}. $bullet", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Open Settings")
        }
    }
}

data class RssFetchSummary(
    val items: List<RssItem>,
    val errors: List<String>
)

private suspend fun fetchRssSignals(
    rssFetcher: RssFetcher,
    feeds: List<String>,
    maxFeeds: Int,
    itemLimit: Int
): RssFetchSummary {
    if (feeds.isEmpty()) {
        return RssFetchSummary(items = emptyList(), errors = emptyList())
    }

    return withContext(Dispatchers.IO) {
        val jobs = feeds.take(maxFeeds).map { feedUrl ->
            async {
                feedUrl to rssFetcher.fetch(feedUrl, limit = itemLimit)
            }
        }

        val results = jobs.awaitAll()
        val items = mutableListOf<RssItem>()
        val errors = mutableListOf<String>()

        results.forEach { (feedUrl, result) ->
            result.onSuccess {
                items.addAll(it)
            }.onFailure {
                errors.add("$feedUrl -> ${it.message ?: "RSS fetch failed"}")
            }
        }

        RssFetchSummary(items = items.take(itemLimit * maxFeeds), errors = errors)
    }
}

private fun clampDisplayWordCount(rawOutput: String): String {
    val words = rawOutput
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }

    if (words.isEmpty()) return "No output generated."

    val expandedWords = words.toMutableList()
    val paddingPhrase = listOf(
        "This", "M4", "display", "preview", "is", "lightweight", "and", "stays", "within",
        "the", "target", "word", "range", "for", "basic", "local", "generation", "testing."
    )
    while (expandedWords.size < 40) {
        expandedWords.addAll(paddingPhrase)
    }

    return expandedWords.take(120).joinToString(" ")
}
