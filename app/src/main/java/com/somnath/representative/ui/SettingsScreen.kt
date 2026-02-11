package com.somnath.representative.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.somnath.representative.scheduler.SchedulerPrefs
import com.somnath.representative.scheduler.SomnathScheduler

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { SchedulerPrefs(context) }

    var runOnlyWhenCharging by remember { mutableStateOf(prefs.isChargingOnly()) }
    var runOnlyOnWifi by remember { mutableStateOf(prefs.isWifiOnly()) }

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

        SettingToggleRow(
            label = "Run only when charging",
            checked = runOnlyWhenCharging,
            onCheckedChange = { runOnlyWhenCharging = it }
        )

        SettingToggleRow(
            label = "Run only on Wi-Fi",
            checked = runOnlyOnWifi,
            onCheckedChange = { runOnlyOnWifi = it }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                prefs.setChargingOnly(runOnlyWhenCharging)
                prefs.setWifiOnly(runOnlyOnWifi)
                SomnathScheduler.apply(context, runOnlyWhenCharging, runOnlyOnWifi)
                Toast.makeText(context, "Scheduler settings applied", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Apply Scheduler Settings")
        }

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Back")
        }
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
