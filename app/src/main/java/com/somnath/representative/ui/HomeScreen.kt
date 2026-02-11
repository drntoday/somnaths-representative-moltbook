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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.somnath.representative.scheduler.SchedulerPrefs
import java.util.Date

@Composable
fun HomeScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { SchedulerPrefs(context) }
    val refreshTick = remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        val sharedPrefs = context.getSharedPreferences("somnath_rep_scheduler", android.content.Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            refreshTick.value += 1
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val refresh = refreshTick.value

    val schedulerStatus = remember(refresh) { if (prefs.isSchedulerEnabled()) "Enabled" else "Disabled" }
    val lastActionAt = remember(refresh) { prefs.getLastActionAtMillis() }
    val lastActionText = remember(refresh) { prefs.getLastActionText() }
    val errorCount = remember(refresh) { prefs.getErrorCount() }

    val lastAction = if (lastActionAt == 0L) {
        "—"
    } else {
        val formatted = DateFormat.format("yyyy-MM-dd HH:mm:ss", Date(lastActionAt)).toString()
        "$lastActionText ($formatted)"
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
        Text(text = "Scheduler: $schedulerStatus", style = MaterialTheme.typography.bodyLarge)
        Text(text = "Last action: $lastAction", style = MaterialTheme.typography.bodyLarge)
        Text(text = "Errors: $errorCount", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Open Settings")
        }
    }
}
