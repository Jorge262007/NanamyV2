package com.nanamy.launcher.calendar

import java.time.LocalDate
import java.time.LocalTime

data class CalendarEntry(
    val title: String,
    val date: LocalDate, // For recurring, this is used during expansion
    val time: LocalTime? = null,
    val allDay: Boolean = false,
    val remind: Boolean = false,
    val notifyBeforeMinutes: Int = 0,
    val isRecurring: Boolean = false,
    val recurringDay: String? = null, // e.g., "Monday"
    val recurringStart: LocalDate? = null,
    val recurringEnd: LocalDate? = null,
    val originalId: String? = null // To identify blocks in the file
)
