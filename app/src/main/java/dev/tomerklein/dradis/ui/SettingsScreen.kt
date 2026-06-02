package dev.tomerklein.dradis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.tomerklein.dradis.ServiceLocator
import dev.tomerklein.dradis.settings.BrokerConfig
import dev.tomerklein.dradis.settings.DradisSettings
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val repo = ServiceLocator.settings
    val scope = rememberCoroutineScope()
    val saved by repo.settings.collectAsState(initial = DradisSettings())

    // Seed editable fields from the persisted settings; re-seed when they load.
    var deviceName by rememberSaveable(saved.deviceName) { mutableStateOf(saved.deviceName) }
    var prefix by rememberSaveable(saved.topicPrefix) { mutableStateOf(saved.topicPrefix) }
    var homeSsids by rememberSaveable(saved.homeSsids) {
        mutableStateOf(saved.homeSsids.joinToString(", "))
    }

    var lanHost by rememberSaveable(saved.lanBroker.host) { mutableStateOf(saved.lanBroker.host) }
    var lanPort by rememberSaveable(saved.lanBroker.port) { mutableStateOf(saved.lanBroker.port.toString()) }
    var lanUser by rememberSaveable(saved.lanBroker.username) { mutableStateOf(saved.lanBroker.username) }
    var lanPass by rememberSaveable(saved.lanBroker.password) { mutableStateOf(saved.lanBroker.password) }
    var lanTls by rememberSaveable(saved.lanBroker.tls) { mutableStateOf(saved.lanBroker.tls) }

    var wanHost by rememberSaveable(saved.wanBroker.host) { mutableStateOf(saved.wanBroker.host) }
    var wanPort by rememberSaveable(saved.wanBroker.port) { mutableStateOf(saved.wanBroker.port.toString()) }
    var wanUser by rememberSaveable(saved.wanBroker.username) { mutableStateOf(saved.wanBroker.username) }
    var wanPass by rememberSaveable(saved.wanBroker.password) { mutableStateOf(saved.wanBroker.password) }
    var wanTls by rememberSaveable(saved.wanBroker.tls) { mutableStateOf(saved.wanBroker.tls) }

    var locEnabled by rememberSaveable(saved.locationPublishEnabled) { mutableStateOf(saved.locationPublishEnabled) }
    var locInterval by rememberSaveable(saved.locationIntervalSeconds) { mutableStateOf(saved.locationIntervalSeconds.toString()) }

    var smsEnabled by rememberSaveable(saved.smsEnabled) { mutableStateOf(saved.smsEnabled) }
    var locationEnabled by rememberSaveable(saved.locationEnabled) { mutableStateOf(saved.locationEnabled) }
    var pingEnabled by rememberSaveable(saved.pingEnabled) { mutableStateOf(saved.pingEnabled) }
    var cameraEnabled by rememberSaveable(saved.cameraEnabled) { mutableStateOf(saved.cameraEnabled) }
    var telemetryEnabled by rememberSaveable(saved.telemetryEnabled) { mutableStateOf(saved.telemetryEnabled) }
    var notifyEnabled by rememberSaveable(saved.notifyEnabled) { mutableStateOf(saved.notifyEnabled) }

    var autostart by rememberSaveable(saved.autostartOnBoot) { mutableStateOf(saved.autostartOnBoot) }
    var reconnect by rememberSaveable(saved.reconnectOnNetworkChange) { mutableStateOf(saved.reconnectOnNetworkChange) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsCard("Device") {
            Field("Device name", deviceName) { deviceName = it }
            Field("Topic prefix", prefix) { prefix = it }
            Field("Home Wi-Fi SSIDs (comma-separated)", homeSsids) { homeSsids = it }
        }

        SettingsCard("LAN broker (home Wi-Fi)") {
            Field("Host", lanHost) { lanHost = it }
            Field("Port", lanPort, KeyboardType.Number) { lanPort = it }
            Field("Username", lanUser) { lanUser = it }
            Field("Password", lanPass, KeyboardType.Password, password = true) { lanPass = it }
            ToggleRow("Use TLS", lanTls) { lanTls = it }
        }

        SettingsCard("WAN broker (mobile / away)") {
            Field("Host", wanHost) { wanHost = it }
            Field("Port", wanPort, KeyboardType.Number) { wanPort = it }
            Field("Username", wanUser) { wanUser = it }
            Field("Password", wanPass, KeyboardType.Password, password = true) { wanPass = it }
            ToggleRow("Use TLS", wanTls) { wanTls = it }
        }

        SettingsCard("Location publishing") {
            ToggleRow("Publish periodically", locEnabled) { locEnabled = it }
            Field("Interval (seconds)", locInterval, KeyboardType.Number) { locInterval = it }
        }

        SettingsCard("Features") {
            ToggleRow("SMS", smsEnabled) { smsEnabled = it }
            ToggleRow("Location", locationEnabled) { locationEnabled = it }
            ToggleRow("Ping / find phone", pingEnabled) { pingEnabled = it }
            ToggleRow("Camera", cameraEnabled) { cameraEnabled = it }
            ToggleRow("Telemetry", telemetryEnabled) { telemetryEnabled = it }
            ToggleRow("Notifications", notifyEnabled) { notifyEnabled = it }
        }

        SettingsCard("Behaviour") {
            ToggleRow("Autostart on boot", autostart) { autostart = it }
            ToggleRow("Reconnect on network change", reconnect) { reconnect = it }
        }

        Button(
            onClick = {
                val next = DradisSettings(
                    deviceName = deviceName.trim().ifBlank { "phone" },
                    topicPrefix = prefix.trim().ifBlank { "zanzito" },
                    lanBroker = BrokerConfig(
                        host = lanHost.trim(),
                        port = lanPort.toIntOrNull() ?: 1883,
                        username = lanUser.trim(),
                        password = lanPass,
                        tls = lanTls,
                    ),
                    wanBroker = BrokerConfig(
                        host = wanHost.trim(),
                        port = wanPort.toIntOrNull() ?: 8883,
                        username = wanUser.trim(),
                        password = wanPass,
                        tls = wanTls,
                    ),
                    homeSsids = homeSsids.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    locationPublishEnabled = locEnabled,
                    locationIntervalSeconds = locInterval.toIntOrNull()?.coerceAtLeast(15) ?: 300,
                    smsEnabled = smsEnabled,
                    locationEnabled = locationEnabled,
                    pingEnabled = pingEnabled,
                    cameraEnabled = cameraEnabled,
                    telemetryEnabled = telemetryEnabled,
                    notifyEnabled = notifyEnabled,
                    autostartOnBoot = autostart,
                    reconnectOnNetworkChange = reconnect,
                )
                scope.launch { repo.set(next) }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save settings") }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
