package com.nanamy.launcher.calendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val parser = CalendarFileParser(context)
            val scheduler = CalendarAlarmScheduler(context)
            
            // Just parse the file and reschedule upcoming alarms
            val now = LocalDate.now()
            val parserFile = parser.javaClass.getDeclaredField("file").apply { isAccessible = true }.get(parser) as java.io.File
            
            if (parserFile.exists()) {
                val entries = parser.getEntriesInRange(now, now.plusMonths(1))
                entries.distinctBy { it.title }.forEach { entry ->
                    scheduler.scheduleAlarm(entry)
                }
            }
        }
    }
}
