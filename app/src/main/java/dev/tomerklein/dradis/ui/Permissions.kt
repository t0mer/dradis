package dev.tomerklein.dradis.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Permission/special-access helpers for the Status screen (CLAUDE.md §8). */
object Permissions {

    /** Runtime permissions requested together in the first prompt.
     *  Background location is requested separately, after foreground is granted. */
    fun runtimePermissions(): Array<String> = buildList {
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun foregroundLocationGranted(context: Context): Boolean =
        isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun backgroundLocationGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isGranted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else true

    fun notificationPolicyGranted(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        return nm?.isNotificationPolicyAccessGranted == true
    }

    fun batteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    // --- Intents to special-access settings screens -------------------------
    fun notificationPolicyIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    fun batteryOptimizationIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
}
