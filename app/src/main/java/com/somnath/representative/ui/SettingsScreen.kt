package com.somnath.representative.ui

import android.content.Intent
import android.net.Uri
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
import androidx.documentfile.provider.DocumentFile
import com.somnath.representative.BuildConfig
import com.somnath.representative.data.ApiKeyStore
import com.somnath.representative.data.ModelPrefs
import com.somnath.representative.data.SchedulerPrefs
import com.somnath.representative.scheduler.SomnathRepScheduler

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val apiKeyStore = remember { ApiKeyStore(context) }
    val modelPrefs = remember { ModelPrefs(context) }

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
    var modelUriString by remember { mutableStateOf(modelPrefs.getModelFolderUriString()) }
    var modelStatus by remember { mutableStateOf("") }

    val modelFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) {
            modelStatus = "Model folder selection canceled"
            return@rememberLauncherForActivityResult
        }

        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, flags)
            modelPrefs.saveModelFolderUri(uri)
            modelUriString = uri.toString()
            modelStatus = "Model folder selected"
        }.onFailure {
            modelStatus = "Could not persist model folder permission"
        }
    }

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
        Text(text = "Phi Model Folder", style = MaterialTheme.typography.titleMedium)
        val selectedFolderName = modelUriString?.let { uriString ->
            DocumentFile.fromTreeUri(context, Uri.parse(uriString))?.name ?: uriString
        } ?: "Not selected"
        Text(text = "Selected: $selectedFolderName", style = MaterialTheme.typography.bodyMedium)

        Button(
            onClick = { modelFolderPicker.launch(null) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Select Model Folder")
        }

        Button(
            onClick = {
                val existing = modelUriString
                if (existing != null) {
                    runCatching {
                        context.contentResolver.releasePersistableUriPermission(
                            Uri.parse(existing),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    }
                }
                modelPrefs.clearModelFolder()
                modelUriString = null
                modelStatus = "Model folder cleared"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Clear Model")
        }

        if (modelStatus.isNotBlank()) {
            Text(text = modelStatus, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Back")
        }
    }
}
