package dev.tomerklein.dradis.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.tomerklein.dradis.ServiceLocator
import dev.tomerklein.dradis.mqtt.Topics
import dev.tomerklein.dradis.settings.BrokerConfig
import dev.tomerklein.dradis.settings.DradisSettings
import dev.tomerklein.dradis.ui.components.ConsoleCard
import dev.tomerklein.dradis.ui.components.Field
import dev.tomerklein.dradis.ui.components.SectionHeader
import dev.tomerklein.dradis.ui.components.ToggleRow
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val repo = ServiceLocator.settings
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
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
    var lanCaCert by rememberSaveable(saved.lanBroker.caCert) { mutableStateOf(saved.lanBroker.caCert) }
    val lanCaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lanCaCert = readTextSafe(context, uri)
    }

    var wanHost by rememberSaveable(saved.wanBroker.host) { mutableStateOf(saved.wanBroker.host) }
    var wanPort by rememberSaveable(saved.wanBroker.port) { mutableStateOf(saved.wanBroker.port.toString()) }
    var wanUser by rememberSaveable(saved.wanBroker.username) { mutableStateOf(saved.wanBroker.username) }
    var wanPass by rememberSaveable(saved.wanBroker.password) { mutableStateOf(saved.wanBroker.password) }
    var wanTls by rememberSaveable(saved.wanBroker.tls) { mutableStateOf(saved.wanBroker.tls) }
    var wanCaCert by rememberSaveable(saved.wanBroker.caCert) { mutableStateOf(saved.wanBroker.caCert) }
    val wanCaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) wanCaCert = readTextSafe(context, uri)
    }

    var periodicEnabled by rememberSaveable(saved.periodicUpdatesEnabled) { mutableStateOf(saved.periodicUpdatesEnabled) }
    var updateInterval by rememberSaveable(saved.updateIntervalSeconds) { mutableStateOf(saved.updateIntervalSeconds.toString()) }

    var smsEnabled by rememberSaveable(saved.smsEnabled) { mutableStateOf(saved.smsEnabled) }
    var smsNotifyOnSend by rememberSaveable(saved.smsNotifyOnSend) { mutableStateOf(saved.smsNotifyOnSend) }
    var locationEnabled by rememberSaveable(saved.locationEnabled) { mutableStateOf(saved.locationEnabled) }
    var locationHighAccuracy by rememberSaveable(saved.locationHighAccuracy) { mutableStateOf(saved.locationHighAccuracy) }
    var pingEnabled by rememberSaveable(saved.pingEnabled) { mutableStateOf(saved.pingEnabled) }
    var alarmDuration by rememberSaveable(saved.alarmDurationSeconds) { mutableStateOf(saved.alarmDurationSeconds.toString()) }
    var alarmOverrideDnd by rememberSaveable(saved.alarmOverrideDnd) { mutableStateOf(saved.alarmOverrideDnd) }
    var alarmRingtone by rememberSaveable(saved.alarmRingtoneUri) { mutableStateOf(saved.alarmRingtoneUri) }
    var alarmRingtoneTitle by rememberSaveable(saved.alarmRingtoneTitle) { mutableStateOf(saved.alarmRingtoneTitle) }
    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            alarmRingtone = uri?.toString().orEmpty()
            alarmRingtoneTitle = uri?.let {
                runCatching { RingtoneManager.getRingtone(context, it)?.getTitle(context) }.getOrNull()
            }.orEmpty()
        }
    }
    var cameraEnabled by rememberSaveable(saved.cameraEnabled) { mutableStateOf(saved.cameraEnabled) }
    var cameraDefaultRear by rememberSaveable(saved.cameraDefaultRear) { mutableStateOf(saved.cameraDefaultRear) }
    var telemetryEnabled by rememberSaveable(saved.telemetryEnabled) { mutableStateOf(saved.telemetryEnabled) }
    var notifyEnabled by rememberSaveable(saved.notifyEnabled) { mutableStateOf(saved.notifyEnabled) }
    var notifyReadAloud by rememberSaveable(saved.notifyReadAloud) { mutableStateOf(saved.notifyReadAloud) }
    var sensorsEnabled by rememberSaveable(saved.sensorsEnabled) { mutableStateOf(saved.sensorsEnabled) }
    var ttsEnabled by rememberSaveable(saved.ttsEnabled) { mutableStateOf(saved.ttsEnabled) }

    var hassEnabled by rememberSaveable(saved.homeAssistantEnabled) { mutableStateOf(saved.homeAssistantEnabled) }
    var hassPrefix by rememberSaveable(saved.homeAssistantPrefix) { mutableStateOf(saved.homeAssistantPrefix) }

    var autostart by rememberSaveable(saved.autostartOnBoot) { mutableStateOf(saved.autostartOnBoot) }
    var reconnect by rememberSaveable(saved.reconnectOnNetworkChange) { mutableStateOf(saved.reconnectOnNetworkChange) }
    var keepAlive by rememberSaveable(saved.keepAliveWhileLocked) { mutableStateOf(saved.keepAliveWhileLocked) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionHeader("Basic")
        SettingsCard("Device") {
            Field("Device name", deviceName) { deviceName = it }
            Field("Topic prefix", prefix, mono = true) { prefix = it }
        }

        SettingsCard("LAN broker (home Wi-Fi)") {
            Field("Home Wi-Fi SSIDs (comma-separated)", homeSsids, mono = true) { homeSsids = it }
            Field("Host", lanHost, mono = true) { lanHost = it }
            Field("Port", lanPort, KeyboardType.Number, mono = true) { lanPort = it }
            Field("Username", lanUser, mono = true) { lanUser = it }
            Field("Password", lanPass, KeyboardType.Password, password = true) { lanPass = it }
            ToggleRow("Use TLS", checked = lanTls) {
                lanTls = it
                lanPort = syncTlsPort(lanPort, it)
            }
            if (lanTls) {
                CertRow(
                    loaded = lanCaCert.isNotBlank(),
                    onPick = { lanCaPicker.launch(CERT_MIME_TYPES) },
                    onClear = { lanCaCert = "" },
                )
            }
        }

        SettingsCard("WAN broker (mobile / away)") {
            Field("Host", wanHost, mono = true) { wanHost = it }
            Field("Port", wanPort, KeyboardType.Number, mono = true) { wanPort = it }
            Field("Username", wanUser, mono = true) { wanUser = it }
            Field("Password", wanPass, KeyboardType.Password, password = true) { wanPass = it }
            ToggleRow("Use TLS", checked = wanTls) {
                wanTls = it
                wanPort = syncTlsPort(wanPort, it)
            }
            if (wanTls) {
                CertRow(
                    loaded = wanCaCert.isNotBlank(),
                    onPick = { wanCaPicker.launch(CERT_MIME_TYPES) },
                    onClear = { wanCaCert = "" },
                )
            }
        }

        SettingsCard("Update modes") {
            ToggleRow(
                "Publish periodically",
                "heartbeat: send telemetry + location on the interval",
                periodicEnabled,
            ) { periodicEnabled = it }
            Field("Update interval (seconds)", updateInterval, KeyboardType.Number, enabled = periodicEnabled) {
                updateInterval = it
            }
        }

        SectionHeader("Outbound")
        SettingsCard("Location") {
            ToggleRow("Enabled", "send GPS location", locationEnabled) { locationEnabled = it }
            ToggleRow("High accuracy", "uses GPS; more battery", locationHighAccuracy, enabled = locationEnabled) {
                locationHighAccuracy = it
            }
        }

        SettingsCard("Telemetry") {
            ToggleRow("Enabled", "battery, Wi-Fi and device info", telemetryEnabled) { telemetryEnabled = it }
        }

        SettingsCard("Sensors") {
            ToggleRow("Enabled", "step counter, step detector, motion", sensorsEnabled) { sensorsEnabled = it }
        }

        SettingsCard("Camera") {
            ToggleRow("Enabled", "capture photos on request", cameraEnabled) { cameraEnabled = it }
            ToggleRow("Default camera: rear", "off = front, when none requested", cameraDefaultRear, enabled = cameraEnabled) {
                cameraDefaultRear = it
            }
        }

        SectionHeader("Inbound")
        SettingsCard("SMS") {
            ToggleRow("Enabled", "send SMS on request", smsEnabled) { smsEnabled = it }
            ToggleRow("Notify when sent", "post a notification after sending", smsNotifyOnSend, enabled = smsEnabled) {
                smsNotifyOnSend = it
            }
        }

        SettingsCard("Notifications") {
            ToggleRow("Enabled", "show pushed notifications", notifyEnabled) { notifyEnabled = it }
            ToggleRow("Read aloud", "speak notifications via TTS", notifyReadAloud, enabled = notifyEnabled) {
                notifyReadAloud = it
            }
        }

        SettingsCard("Text-to-speech") {
            ToggleRow("Enabled", "speak text sent to .../say", ttsEnabled) { ttsEnabled = it }
        }

        SettingsCard("Alarm (find phone)") {
            ToggleRow("Enabled", "sound an alarm on request", pingEnabled) { pingEnabled = it }
            Field("Alarm duration (seconds)", alarmDuration, KeyboardType.Number, enabled = pingEnabled) {
                alarmDuration = it
            }
            RingtoneRow(
                title = alarmRingtoneTitle.ifBlank { "Default alarm" },
                enabled = pingEnabled,
                onPick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select alarm ringtone")
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        putExtra(
                            RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM),
                        )
                        if (alarmRingtone.isNotBlank()) {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(alarmRingtone))
                        }
                    }
                    runCatching { ringtonePicker.launch(intent) }
                },
            )
            ToggleRow("Override silent / DND", "ring through Do-Not-Disturb", alarmOverrideDnd, enabled = pingEnabled) {
                alarmOverrideDnd = it
            }
        }

        SectionHeader("Integrations")
        SettingsCard("Home Assistant") {
            ToggleRow("MQTT discovery", "auto-create the device + entities in HA", hassEnabled) { hassEnabled = it }
            Field("Discovery prefix", hassPrefix, enabled = hassEnabled, mono = true) { hassPrefix = it }
        }

        SectionHeader("Behaviour")
        SettingsCard("Service") {
            ToggleRow("Autostart on boot", checked = autostart) { autostart = it }
            ToggleRow("Reconnect on network change", checked = reconnect) { reconnect = it }
            ToggleRow("Keep connected while asleep", checked = keepAlive) { keepAlive = it }
        }

        Button(
            onClick = {
                // copy() from the loaded settings so non-UI fields (e.g. the
                // persisted per-install clientId) are preserved — building a fresh
                // DradisSettings() here would reset them to defaults on every save.
                val next = saved.copy(
                    deviceName = deviceName.trim().ifBlank { "phone" },
                    topicPrefix = prefix.trim().ifBlank { Topics.DEFAULT_PREFIX },
                    lanBroker = BrokerConfig(
                        host = lanHost.trim(),
                        port = lanPort.toIntOrNull() ?: 1883,
                        username = lanUser.trim(),
                        password = lanPass,
                        tls = lanTls,
                        caCert = lanCaCert.trim(),
                    ),
                    wanBroker = BrokerConfig(
                        host = wanHost.trim(),
                        port = wanPort.toIntOrNull() ?: 8883,
                        username = wanUser.trim(),
                        password = wanPass,
                        tls = wanTls,
                        caCert = wanCaCert.trim(),
                    ),
                    homeSsids = homeSsids.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    periodicUpdatesEnabled = periodicEnabled,
                    updateIntervalSeconds = updateInterval.toIntOrNull()?.coerceAtLeast(15) ?: 90,
                    smsEnabled = smsEnabled,
                    locationEnabled = locationEnabled,
                    pingEnabled = pingEnabled,
                    cameraEnabled = cameraEnabled,
                    telemetryEnabled = telemetryEnabled,
                    notifyEnabled = notifyEnabled,
                    sensorsEnabled = sensorsEnabled,
                    ttsEnabled = ttsEnabled,
                    smsNotifyOnSend = smsNotifyOnSend,
                    locationHighAccuracy = locationHighAccuracy,
                    alarmDurationSeconds = alarmDuration.toIntOrNull()?.coerceIn(1, 600) ?: 30,
                    alarmOverrideDnd = alarmOverrideDnd,
                    alarmRingtoneUri = alarmRingtone,
                    alarmRingtoneTitle = alarmRingtoneTitle,
                    cameraDefaultRear = cameraDefaultRear,
                    notifyReadAloud = notifyReadAloud,
                    homeAssistantEnabled = hassEnabled,
                    homeAssistantPrefix = hassPrefix.trim().ifBlank { "homeassistant" },
                    autostartOnBoot = autostart,
                    reconnectOnNetworkChange = reconnect,
                    keepAliveWhileLocked = keepAlive,
                )
                scope.launch { repo.set(next) }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save settings") }
    }
}

private val CERT_MIME_TYPES = arrayOf(
    "application/x-pem-file", "application/x-x509-ca-cert", "application/pkix-cert",
    "application/octet-stream", "text/plain", "*/*",
)

/** Read a picked document's text (PEM certificate), or "" on failure. */
private fun readTextSafe(context: Context, uri: Uri): String =
    runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
    }.getOrDefault("")

@Composable
private fun RingtoneRow(title: String, enabled: Boolean, onPick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Ringtone")
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onPick, enabled = enabled) { Text("Choose") }
    }
}

@Composable
private fun CertRow(loaded: Boolean, onPick: () -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("CA certificate")
            Text(
                if (loaded) "Loaded" else "none — uses system CAs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (loaded) TextButton(onClick = onClear) { Text("Clear") }
        OutlinedButton(onClick = onPick) { Text(if (loaded) "Replace" else "Choose") }
    }
}

/** Keep the broker port in step with the TLS toggle: swap between the standard
 *  1883 (plain) and 8883 (TLS) ports, but leave a custom port untouched. */
private fun syncTlsPort(current: String, tls: Boolean): String = when {
    tls && (current.isBlank() || current.trim() == "1883") -> "8883"
    !tls && (current.isBlank() || current.trim() == "8883") -> "1883"
    else -> current
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    ConsoleCard {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}
