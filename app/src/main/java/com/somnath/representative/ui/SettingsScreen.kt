package com.somnath.representative.ui

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.somnath.representative.BuildConfig
import com.somnath.representative.data.ApiKeyStore
import com.somnath.representative.data.LocalModelPrefs
import com.somnath.representative.data.SchedulerPrefs
import com.somnath.representative.scheduler.SomnathRepScheduler

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val apiKeyStore = remember { ApiKeyStore(context) }

    var chargingOnly by remember { mutableStateOf(SchedulerPrefs.isChargingOnly(context)) }
    var wifiOnly by remember { mutableStateOf(SchedulerPrefs.isWifiOnly(context)) }
    var enableDebugTools by remember { mutableStateOf(BuildConfig.DEBUG && SchedulerPrefs.isDebugToolsEnabled(context)) }

    LaunchedEffect(Unit) {
        if (!BuildConfig.DEBUG) {
            SchedulerPrefs.setDebugToolsEnabled(context, false)
            enableDebugTools = false
        }
    }
    var apiKeyInput by remember { mutableStateOf("") }
    var apiKeyStatus by remember { mutableStateOf("") }
    var selectedModelUri by remember { mutableStateOf(LocalModelPrefs.getModelUri(context)) }
    var selectedModelName by remember { mutableStateOf(selectedModelUri?.let { getDisplayName(context, it) } ?: "None selected") }

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri == null) {
                return@rememberLauncherForActivityResult
            }

            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val displayName = getDisplayName(context, uri.toString())
                if (!displayName.lowercase().endsWith(".gguf")) {
                    throw IllegalArgumentException("Please select a .gguf file")
                }
                LocalModelPrefs.setModelUri(context, uri.toString())
                selectedModelUri = uri.toString()
                selectedModelName = displayName
            }.onFailure {
                selectedModelName = "Failed to store selected model"
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
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
        Text(text = "Local Model (GGUF)", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Download a Phi GGUF model to your phone, then select it here. The app runs offline.",
            style = MaterialTheme.typography.bodySmall
        )
        Text(text = "Selected: $selectedModelName", style = MaterialTheme.typography.bodyMedium)

        Button(
            onClick = { modelPickerLauncher.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Select GGUF Model File")
        }

        Button(
            onClick = {
                selectedModelUri?.let { uriString ->
                    runCatching {
                        context.contentResolver.releasePersistableUriPermission(
                            android.net.Uri.parse(uriString),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                }
                LocalModelPrefs.clearModelUri(context)
                selectedModelUri = null
                selectedModelName = "None selected"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Clear Model")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Back")
        }
    }
}

private fun getDisplayName(context: android.content.Context, uriString: String): String {
    val uri = android.net.Uri.parse(uriString)
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) {
            return cursor.getString(idx) ?: "Selected model"
        }
    }
    return uri.lastPathSegment ?: "Selected model"
}
