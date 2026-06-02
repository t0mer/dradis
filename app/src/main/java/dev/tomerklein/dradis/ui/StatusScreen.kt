package dev.tomerklein.dradis.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tomerklein.dradis.ServiceLocator
import dev.tomerklein.dradis.mqtt.ConnState
import dev.tomerklein.dradis.mqtt.MqttService

@Composable
fun StatusScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val connection by ServiceLocator.connection.collectAsState()

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* result handled by re-reading state */ }

    val bgLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Connection", fontWeight = FontWeight.Bold)
                Row("State", connection.state.name)
                Row("Broker", connection.broker.name)
                Row("Wi-Fi SSID", connection.ssid ?: "—")
                Row("Host", connection.host ?: "—")
                if (connection.detail.isNotBlank()) Row("Detail", connection.detail)
            }
        }

        Button(onClick = { MqttService.start(context) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (connection.state == ConnState.CONNECTED) "Restart service" else "Start service")
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Permissions", fontWeight = FontWeight.Bold)

                OutlinedButton(
                    onClick = { runtimeLauncher.launch(Permissions.runtimePermissions()) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Grant SMS / Location / Camera / Notifications") }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    OutlinedButton(
                        onClick = {
                            if (Permissions.foregroundLocationGranted(context)) {
                                bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            }
                        },
                        enabled = Permissions.foregroundLocationGranted(context) &&
                            !Permissions.backgroundLocationGranted(context),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Allow background location (\"Allow all the time\")") }
                }

                OutlinedButton(
                    onClick = { context.startActivity(Permissions.notificationPolicyIntent()) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Do-Not-Disturb access (for find-my-phone)") }

                OutlinedButton(
                    onClick = { context.startActivity(Permissions.batteryOptimizationIntent(context)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Ignore battery optimisation") }
            }
        }
    }
}

@Composable
private fun Row(label: String, value: String) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
        Text("$label: ", fontWeight = FontWeight.SemiBold)
        Text(value)
    }
}
