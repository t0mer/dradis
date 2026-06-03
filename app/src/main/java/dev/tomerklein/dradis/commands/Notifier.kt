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
import java.util.concurrent.atomic.AtomicInteger

/**
 * Posts notifications to the device shade on a dedicated high-importance channel,
 * shared by [NotifyHandler] (the `notify` command) and [SmsHandler] (notify on
 * SMS sent). Separate from the persistent foreground-service channel.
 */
object Notifier {

    private const val CHANNEL_ID = "dradis_push"
    private val autoId = AtomicInteger(2000)

    fun nextId(): Int = autoId.incrementAndGet()

    /** Post a notification. Returns false if notifications are disabled for the app. */
    @SuppressLint("MissingPermission")
    fun post(context: Context, title: String, text: String, id: Int = nextId()): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        ensureChannel(context)
        val tapIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title.ifBlank { "Dradis" })
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
        return true
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
}
