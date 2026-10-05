package com.nanamy.launcher.calendar

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nanamy.launcher.MainActivity
import java.time.LocalDate

class CalendarReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Event Reminder"
        val isRecurring = intent.getBooleanExtra("isRecurring", false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        showNotification(context, title)

        if (isRecurring) {
            scheduleNextRecurring(context, title)
        }
    }

    private fun showNotification(context: Context, title: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "nanamy_calendar"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Nanamy Calendar", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, openIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setContentTitle("Calendar Event")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(title.hashCode(), notification)
    }

    private fun scheduleNextRecurring(context: Context, title: String) {
        val parser = CalendarFileParser(context)
        val now = LocalDate.now()
        // We look for the entry to find its day/time
        val entries = parser.getEntriesInRange(now, now.plusWeeks(2))
        val entry = entries.find { it.title == title && it.isRecurring }
        if (entry != null) {
            CalendarAlarmScheduler(context).scheduleAlarm(entry)
        }
    }
}
