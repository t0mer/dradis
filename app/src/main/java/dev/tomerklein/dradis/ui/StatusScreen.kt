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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import dev.tomerklein.dradis.ServiceLocator
import dev.tomerklein.dradis.mqtt.ConnState
import dev.tomerklein.dradis.mqtt.MqttService
import dev.tomerklein.dradis.settings.DradisSettings
import dev.tomerklein.dradis.ui.components.ConsoleCard
import dev.tomerklein.dradis.ui.components.KeyValueRow
import dev.tomerklein.dradis.ui.components.SectionHeader
import dev.tomerklein.dradis.ui.components.StateChip
import dev.tomerklein.dradis.ui.components.chipStateFor

@Composable
fun StatusScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val connection by ServiceLocator.connection.collectAsState()
    val settings by ServiceLocator.settings.settings.collectAsState(initial = DradisSettings())

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* result handled by re-reading state */ }

    val bgLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // Permission checks are plain reads, so re-run them when the user comes back
    // from a system Settings screen (e.g. App info after allowing restricted settings).
    var resumes by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val owner = ProcessLifecycleOwner.get().lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumes++
        }
        owner.addObserver(observer)
        onDispose { owner.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Without "Allow all the time", the background service can't read the
        // Wi-Fi SSID, so it can't tell it's on home Wi-Fi and keeps switching
        // between the LAN and WAN brokers — repeatedly dropping the MQTT link.
        // Only warn when home SSIDs are actually configured (i.e. LAN/WAN
        // selection depends on the SSID) and foreground location is already on.
        val bgLocationMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            settings.homeSsids.isNotEmpty() &&
            Permissions.foregroundLocationGranted(context) &&
            !Permissions.backgroundLocationGranted(context)
        if (bgLocationMissing) {
            ConsoleCard(error = true) {
                Text("Background location needed", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Location is only set to “While in use”. In the background " +
                        "the app can’t read your Wi-Fi network name, so it keeps " +
                        "switching between the LAN and WAN brokers and the connection " +
                        "drops repeatedly. Set location to “Allow all the time” " +
                        "to keep it stable on home Wi-Fi.",
                )
                Button(
                    onClick = {
                        bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Allow all the time") }
            }
        }

        // Android 15 grays out the SMS "Allow" button for apps installed from an
        // APK file until "Allow restricted settings" is turned on in App info.
        val smsRestricted = remember(resumes, settings.smsEnabled) {
            settings.smsEnabled && Permissions.smsBlockedByRestrictedSettings(context)
        }
        if (smsRestricted) {
            ConsoleCard(error = true) {
                Text("SMS permission blocked", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Android restricts SMS access for apps installed from an APK " +
                        "file, so “Allow” is grayed out (“App was denied access”). " +
                        "To unblock it once: tap “Grant SMS / Location / Camera / " +
                        "Notifications” below and dismiss the prompt, then open App " +
                        "info → ⋮ → Allow restricted settings, confirm, and allow SMS " +
                        "under Permissions.",
                )
                Button(
                    onClick = { context.startActivity(Permissions.appDetailsIntent(context)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open App info") }
            }
        }

        ConsoleCard(hero = true) {
            SectionHeader("Connection")
            StateChip(connection.state.name, chipStateFor(connection.state))
            KeyValueRow("Broker", connection.broker.name)
            KeyValueRow("Wi-Fi SSID", connection.ssid ?: "—")
            KeyValueRow("Host", connection.host ?: "—")
            if (connection.detail.isNotBlank()) KeyValueRow("Detail", connection.detail)
        }

        Button(onClick = { MqttService.start(context) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (connection.state == ConnState.CONNECTED) "Restart service" else "Start service")
        }

        ConsoleCard {
            SectionHeader("Permissions")

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
