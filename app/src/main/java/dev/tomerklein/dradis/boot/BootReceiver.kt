package dev.tomerklein.dradis.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.tomerklein.dradis.ServiceLocator
import dev.tomerklein.dradis.mqtt.MqttService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "dradis.boot"

/**
 * Autostarts the MQTT service after boot when the user has enabled autostart.
 * On MIUI the user must additionally grant "Autostart" in system settings
 * (CLAUDE.md §10) — RECEIVE_BOOT_COMPLETED alone is not honoured there.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val autostart = ServiceLocator.settings.settings.first().autostartOnBoot
                if (autostart) {
                    Log.i(TAG, "Boot completed; autostart enabled — starting DRADIS service")
                    MqttService.start(appContext)
                } else {
                    Log.i(TAG, "Boot completed; autostart disabled")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "boot autostart failed", t)
            } finally {
                pending.finish()
            }
        }
    }
}
