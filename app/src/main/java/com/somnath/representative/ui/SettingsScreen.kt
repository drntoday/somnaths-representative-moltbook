package com.somnath.representative.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.somnath.representative.ai.PhiDownloadStatus
import com.somnath.representative.ai.PhiModelManager
import com.somnath.representative.BuildConfig
import com.somnath.representative.data.ApiKeyStore
import com.somnath.representative.data.SchedulerPrefs
import com.somnath.representative.scheduler.SomnathRepScheduler
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val apiKeyStore = remember { ApiKeyStore(context) }
    val phiModelManager = remember { PhiModelManager(context) }
    val coroutineScope = rememberCoroutineScope()

    var chargingOnly by remember { mutableStateOf(SchedulerPrefs.isChargingOnly(context)) }
    var wifiOnly by remember { mutableStateOf(SchedulerPrefs.isWifiOnly(context)) }
    var enableDebugTools by remember { mutableStateOf(BuildConfig.DEBUG && SchedulerPrefs.isDebugToolsEnabled(context)) }
    var autonomousModeEnabled by remember { mutableStateOf(SchedulerPrefs.isAutonomousModeEnabled(context)) }
    var emergencyStopEnabled by remember { mutableStateOf(SchedulerPrefs.isEmergencyStopEnabled(context)) }
    var autoDownloadModel by remember { mutableStateOf(phiModelManager.isAutoDownloadEnabled()) }
    var downloadWifiOnly by remember { mutableStateOf(phiModelManager.isWifiOnlyEnabled()) }
    var downloadStatus by remember { mutableStateOf(phiModelManager.getInitialStatus()) }

    LaunchedEffect(Unit) {
        if (!BuildConfig.DEBUG) {
            SchedulerPrefs.setDebugToolsEnabled(context, false)
            enableDebugTools = false
        }
    }
    var apiKeyInput by remember { mutableStateOf("") }
    var apiKeyStatus by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(text = "Run only when charging", style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = chargingOnly,
            onCheckedChange = { chargingOnly = it }
        )

        Text(text = "Run only on Wi-Fi", style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = wifiOnly,
            onCheckedChange = { wifiOnly = it }
        )

        if (BuildConfig.DEBUG) {
            Text(text = "Enable Debug Tools", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = enableDebugTools,
                onCheckedChange = {
                    enableDebugTools = it
                    SchedulerPrefs.setDebugToolsEnabled(context, it)
                }
            )
        }


        Text(text = "Autonomous Mode (Safe)", style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = autonomousModeEnabled,
            onCheckedChange = {
                autonomousModeEnabled = it
                SchedulerPrefs.setAutonomousModeEnabled(context, it)
                if (SchedulerPrefs.isSchedulerEnabled(context)) {
                    SomnathRepScheduler.schedule(context, chargingOnly, wifiOnly)
                }
            }
        )
        Text(
            text = "When enabled, app may post automatically under strict limits.",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(text = "Emergency Stop (Disable all posting)", style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = emergencyStopEnabled,
            onCheckedChange = {
                emergencyStopEnabled = it
                SchedulerPrefs.setEmergencyStopEnabled(context, it)
            }
        )

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                SchedulerPrefs.setSchedulerSettings(context, chargingOnly, wifiOnly)
                SchedulerPrefs.setSchedulerEnabled(context, true)
                SomnathRepScheduler.schedule(context, chargingOnly, wifiOnly)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Apply Scheduler Settings")
        }

        Button(
            onClick = {
                SomnathRepScheduler.cancel(context)
                SchedulerPrefs.setSchedulerEnabled(context, false)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Disable Scheduler")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Moltbook API Key", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = { apiKeyInput = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        Button(
            onClick = {
                if (apiKeyInput.isBlank()) {
                    apiKeyStatus = "Enter an API key first"
                    return@Button
                }
                apiKeyStore.saveApiKey(apiKeyInput)
                apiKeyInput = ""
                apiKeyStatus = "Saved"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Save API Key")
        }

        Button(
            onClick = {
                apiKeyStore.clearApiKey()
                apiKeyInput = ""
                apiKeyStatus = "Cleared"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Clear API Key")
        }

        if (apiKeyStatus.isNotBlank()) {
            Text(text = apiKeyStatus, style = MaterialTheme.typography.bodyMedium)
        }


        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Offline Phi Model (Hugging Face)", style = MaterialTheme.typography.titleMedium)
        Text(text = "Needs ~3 GB free", style = MaterialTheme.typography.bodyMedium)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Auto-download model on first open", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = autoDownloadModel,
                onCheckedChange = {
                    autoDownloadModel = it
                    phiModelManager.setAutoDownloadEnabled(it)
                }
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Download on Wi-Fi only", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = downloadWifiOnly,
                onCheckedChange = {
                    downloadWifiOnly = it
                    phiModelManager.setWifiOnlyEnabled(it)
                }
            )
        }

        Button(
            onClick = {
                coroutineScope.launch {
                    downloadStatus = phiModelManager.downloadModel { status ->
                        downloadStatus = status
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Download Phi Model Now")
        }

        Button(
            onClick = {
                val deleted = phiModelManager.deleteModel()
                downloadStatus = if (deleted) {
                    PhiDownloadStatus.NotDownloaded
                } else {
                    PhiDownloadStatus.Error("Unable to delete downloaded model.")
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Delete Downloaded Model")
        }

        val modelStatusText = when (val status = downloadStatus) {
            is PhiDownloadStatus.NotDownloaded -> "Status: Not downloaded"
            is PhiDownloadStatus.Ready -> "Status: Ready"
            is PhiDownloadStatus.Error -> "Status: ${status.message}"
            is PhiDownloadStatus.Downloading -> {
                val pct = status.percent?.let { " ($it%)" } ?: ""
                "Status: Downloading file ${status.fileIndex} of ${status.totalFiles}$pct"
            }
        }
        Text(text = modelStatusText, style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Back")
        }
    }
}
