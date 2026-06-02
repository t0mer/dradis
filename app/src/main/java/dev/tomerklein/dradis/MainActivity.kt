package dev.tomerklein.dradis

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import dev.tomerklein.dradis.mqtt.MqttService
import dev.tomerklein.dradis.ui.DradisTheme
import dev.tomerklein.dradis.ui.MainScreen

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // The foreground-service notification needs POST_NOTIFICATIONS on 13+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Bring the persistent MQTT service up; it self-heals on settings/network change.
        MqttService.start(this)

        setContent {
            DradisTheme {
                MainScreen()
            }
        }
    }
}
