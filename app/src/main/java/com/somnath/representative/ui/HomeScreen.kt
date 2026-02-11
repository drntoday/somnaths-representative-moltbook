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
import com.somnath.representative.moltbook.HttpMoltbookApiClient
import com.somnath.representative.moltbook.MoltbookPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

@Composable
fun HomeScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val homeStatus = remember { mutableStateOf(SchedulerPrefs.getHomeStatus(context)) }
    val coroutineScope = rememberCoroutineScope()
    val client = remember { HttpMoltbookApiClient(apiKeyProvider = { ApiKeyStore.getApiKey(context) }) }

    var m3Status by remember { mutableStateOf("Idle") }
    var latestFeedPosts by remember { mutableStateOf<List<MoltbookPost>>(emptyList()) }

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

        Button(
            onClick = {
                coroutineScope.launch {
                    m3Status = "Fetching feed..."
                    val submolts = SubmoltConfigLoader.loadDefaultSubmolts(context)
                    runCatching {
                        withContext(Dispatchers.IO) {
                            client.fetchFeed(submolts = submolts, limit = 10)
                        }
                    }.onSuccess { posts ->
                        latestFeedPosts = posts
                        m3Status = if (posts.isEmpty()) {
                            "Fetch success: 0 posts"
                        } else {
                            "Fetch success: first post '${posts.first().title.ifBlank { "(untitled)" }}'"
                        }
                    }.onFailure { error ->
                        m3Status = "Fetch failed: ${error.message ?: "unknown error"}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Test: Fetch Feed")
        }

        Button(
            onClick = {
                coroutineScope.launch {
                    val firstPost = latestFeedPosts.firstOrNull()
                    if (firstPost == null) {
                        m3Status = "Post comment skipped: fetch feed first and ensure at least 1 post."
                        return@launch
                    }

                    m3Status = "Posting test comment..."
                    runCatching {
                        withContext(Dispatchers.IO) {
                            client.postComment(
                                postId = firstPost.id,
                                body = "Test comment from Somnath’s Representative (M3)"
                            )
                        }
                    }.onSuccess { success ->
                        m3Status = if (success) {
                            "Post comment success on post '${firstPost.title.ifBlank { firstPost.id }}'"
                        } else {
                            "Post comment failed."
                        }
                    }.onFailure { error ->
                        m3Status = "Post comment failed: ${error.message ?: "unknown error"}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Test: Post Comment")
        }

        Text(text = m3Status, style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Open Settings")
        }
    }
}
