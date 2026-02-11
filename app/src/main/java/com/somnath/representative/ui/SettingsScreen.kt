package com.somnath.representative.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.somnath.representative.data.ApiKeyStore
import com.somnath.representative.data.SchedulerPrefs
import com.somnath.representative.scheduler.SomnathRepScheduler

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var chargingOnly by remember { mutableStateOf(SchedulerPrefs.isChargingOnly(context)) }
    var wifiOnly by remember { mutableStateOf(SchedulerPrefs.isWifiOnly(context)) }
    var apiKeyInput by remember { mutableStateOf("") }
    var apiKeyMessage by remember { mutableStateOf("") }

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
            visualTransformation = PasswordVisualTransformation(),
            placeholder = { Text("Enter API key") }
        )

        Button(
            onClick = {
                if (apiKeyInput.isBlank()) {
                    apiKeyMessage = "API key is empty."
                } else {
                    ApiKeyStore.saveApiKey(context, apiKeyInput)
                    apiKeyInput = ""
                    apiKeyMessage = "API key saved."
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Save API Key")
        }

        Button(
            onClick = {
                ApiKeyStore.clearApiKey(context)
                apiKeyInput = ""
                apiKeyMessage = "API key cleared."
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Clear API Key")
        }

        if (apiKeyMessage.isNotBlank()) {
            Text(text = apiKeyMessage, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Back")
        }
    }
}
