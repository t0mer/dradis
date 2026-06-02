package dev.tomerklein.dradis.commands

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.tomerklein.dradis.MainActivity
import dev.tomerklein.dradis.R
import dev.tomerklein.dradis.mqtt.Topics
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicInteger

/**
 * On `notify`, posts a notification to the device's shade (CLAUDE.md §6.1).
 * Payload is JSON `{"title","text","id"?}`; a non-JSON body is used as the
 * notification text. Posts on a dedicated high-importance channel, separate
 * from the persistent foreground-service channel.
 */
class NotifyHandler : CommandHandler {

    private val json = Json { ignoreUnknownKeys = true }
    private val autoId = AtomicInteger(NOTIFY_ID_BASE)

    override fun handles(topic: String, topics: Topics): Boolean =
        topic == topics.notify

    // Guarded at runtime by areNotificationsEnabled() (false when POST_NOTIFICATIONS
    // is not granted on API 33+), which lint does not recognise.
    @SuppressLint("MissingPermission")
    override suspend fun handle(topic: String, payload: String, sink: CommandSink) {
        if (!sink.settings.notifyEnabled) {
            sink.logInfo("Notifications disabled in settings; ignoring")
            return
        }
        val cmd = payload.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<NotifyCommand>(it) }.getOrNull() }
            ?: NotifyCommand(text = payload)

        if (cmd.title.isBlank() && cmd.text.isBlank()) {
            sink.logInfo("Notify ignored: empty payload")
            return
        }

        val context = sink.appContext
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            sink.logInfo("Notify requested but notifications are disabled for DRADIS")
            return
        }

        ensureChannel(context)
        val tapIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(cmd.title.ifBlank { "DRADIS" })
            .setContentText(cmd.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cmd.text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .build()

        val id = cmd.id ?: autoId.incrementAndGet()
        NotificationManagerCompat.from(context).notify(id, notification)
        sink.logInfo("Notification posted (id=$id): ${cmd.title}")
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Pushed notifications",
                        NotificationManager.IMPORTANCE_HIGH,
                    )
                )
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "dradis_push"
        private const val NOTIFY_ID_BASE = 2000
    }
}
