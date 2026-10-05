package com.nanamy.launcher.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * Utility helper for verifying NotificationListenerService permission and rebinding.
 */
object SetupHelper {

    private const val TAG = "SetupHelper"

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val packageName = context.packageName
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }

    fun openNotificationListenerSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open notification listener settings", e)
        }
    }

    fun forceRebind(context: Context) {
        try {
            val component = ComponentName(context, NanamyNotificationListener::class.java)
            NotificationListenerService.requestRebind(component)
            Log.d(TAG, "Requested rebind for NanamyNotificationListener")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to force rebind listener", e)
        }
    }
}
