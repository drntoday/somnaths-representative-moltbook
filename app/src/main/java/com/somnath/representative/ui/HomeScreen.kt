package com.somnath.representative.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.somnath.representative.data.SchedulerPrefs
import com.somnath.representative.data.SubmoltConfigLoader
import com.somnath.representative.moltbook.OkHttpMoltbookApi
import com.somnath.representative.moltbook.PostSummary
import kotlinx.coroutines.launch
import java.util.Date

@Composable
fun HomeScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val homeStatus = remember { mutableStateOf(SchedulerPrefs.getHomeStatus(context)) }
    val submolts = remember { SubmoltConfigLoader().load(context) }
    val apiKeyStore = remember { ApiKeyStore(context) }
    val moltbookApi = remember { OkHttpMoltbookApi(apiKeyProvider = { apiKeyStore.getApiKey() }) }

    var fetchedPosts by remember { mutableStateOf<List<PostSummary>>(emptyList()) }
    var m3Status by remember { mutableStateOf("Idle") }

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
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Open Settings")
        }
    }
}
