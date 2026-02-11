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
import com.somnath.representative.data.ApiKeyStore
import com.somnath.representative.data.RssFeedConfigLoader
import com.somnath.representative.data.SchedulerPrefs
import com.somnath.representative.data.SubmoltConfigLoader
import com.somnath.representative.factpack.FactPack
import com.somnath.representative.factpack.FactPackBuilder
import com.somnath.representative.inference.LocalReadyPhiInferenceEngine
import com.somnath.representative.moltbook.OkHttpMoltbookApi
import com.somnath.representative.moltbook.PostSummary
import com.somnath.representative.rss.RssFetcher
import com.somnath.representative.rss.RssItem
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
    val submolts = remember { SubmoltConfigLoader().load(context) }
    val rssFeedLoader = remember { RssFeedConfigLoader() }
    val searchProviderConfigLoader = remember { SearchProviderConfigLoader() }
    val apiKeyStore = remember { ApiKeyStore(context) }
    val moltbookApi = remember { OkHttpMoltbookApi(apiKeyProvider = { apiKeyStore.getApiKey() }) }
    val phiInferenceEngine = remember { LocalReadyPhiInferenceEngine() }
    val rssFetcher = remember { RssFetcher() }
    val searchVerifier = remember { StubSearchVerifier() }
    val factPackBuilder = remember { FactPackBuilder() }

    var fetchedPosts by remember { mutableStateOf<List<PostSummary>>(emptyList()) }
    var m3Status by remember { mutableStateOf("Idle") }
    var m4Prompt by remember {
        mutableStateOf("Write a calm 2-sentence reply about building Android apps.")
    }
    var m4Status by remember { mutableStateOf("Idle") }

    var m5Topic by remember { mutableStateOf("Latest Android AI release") }
    var m5Status by remember { mutableStateOf("Idle") }
    var m5FactPack by remember { mutableStateOf<FactPack?>(null) }

    fun refreshStatus() {
        homeStatus.value = SchedulerPrefs.getHomeStatus(context)
    }

    LaunchedEffect(Unit) {
        refreshStatus()
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
            text = "Last action: $lastAction",
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
                    if (fetchedPosts.isEmpty()) {
                        m3Status = "Fetch feed first so a post is available for commenting."
                        return@launch
                    }

                    val targetPostId = fetchedPosts.first().id
                    val result = moltbookApi.postComment(
                        postId = targetPostId,
                        body = "Test comment from Somnath’s Representative (M3)"
                    )
                    m3Status = result.fold(
                        onSuccess = { "Post comment succeeded on post: $targetPostId" },
                        onFailure = { it.message ?: "Post comment failed" }
                    )
                }
            },
            enabled = hasSubmolts,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Test: Post Comment")
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
                val generationResult = phiInferenceEngine.generate(prompt = m4Prompt)
                m4Status = generationResult.fold(
                    onSuccess = { clampDisplayWordCount(it) },
                    onFailure = { error -> error.message ?: "Generation failed" }
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Test: Generate (Phi)")
        }
        Text(text = "M4 output: $m4Status", style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "M5 Test", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        TextField(
            value = m5Topic,
            onValueChange = { m5Topic = it },
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
