package com.nanamy.launcher.calendar

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.LocalDateTime
import java.time.ZoneId

class CalendarAlarmScheduler(private val context: Context) {

    fun scheduleAlarm(entry: CalendarEntry) {
        if (!entry.remind && entry.notifyBeforeMinutes <= 0) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w("NanamyCalendar", "Cannot schedule exact alarms. Permission missing.")
                return
            }
        }

        // Calculate trigger time
        val baseTime = if (entry.isRecurring) {
            // For recurring, we need the next occurrence
            getNextOccurrence(entry)
        } else {
            entry.date.atTime(entry.time ?: java.time.LocalTime.NOON)
        } ?: return

        // 1. Exact reminder
        if (entry.remind) {
            setAlarm(alarmManager, baseTime, entry.title, entry.isRecurring)
        }

        // 2. Notify before
        if (entry.notifyBeforeMinutes > 0) {
            val beforeTime = baseTime.minusMinutes(entry.notifyBeforeMinutes.toLong())
            if (beforeTime.isAfter(LocalDateTime.now())) {
                setAlarm(alarmManager, beforeTime, "Reminder: ${entry.title}", entry.isRecurring)
            }
        }
    }

    private fun setAlarm(alarmManager: AlarmManager, time: LocalDateTime, title: String, isRecurring: Boolean) {
        val intent = Intent(context, CalendarReminderReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("isRecurring", isRecurring)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context, 
            title.hashCode(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun getNextOccurrence(entry: CalendarEntry): LocalDateTime? {
        val now = LocalDateTime.now()
        val dayOfWeek = java.time.DayOfWeek.valueOf(entry.recurringDay!!.uppercase())
        val time = entry.time ?: java.time.LocalTime.NOON
        
        var next = entry.recurringStart!!.atTime(time)
        while (next.isBefore(now) || next.dayOfWeek != dayOfWeek) {
            if (next.dayOfWeek == dayOfWeek) {
                next = next.plusWeeks(1)
            } else {
                next = next.plusDays(1)
            }
        }

        return if (next.isAfter(entry.recurringEnd!!.atTime(23, 59))) null else next
    }

    fun cancelAlarm(title: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, CalendarReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 
            title.hashCode(), 
            intent, 
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
}
