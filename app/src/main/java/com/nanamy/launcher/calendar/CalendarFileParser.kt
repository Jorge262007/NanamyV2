package com.nanamy.launcher.calendar

import android.content.Context
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.DayOfWeek

class CalendarFileParser(private val context: Context) {

    private val fileName = "calendar.txt"
    private val file: File by lazy { File(context.filesDir, fileName) }

    fun getEntriesInRange(from: LocalDate, to: LocalDate): List<CalendarEntry> {
        val allEntries = parseFile()
        val result = mutableListOf<CalendarEntry>()

        for (entry in allEntries) {
            if (entry.isRecurring) {
                // Expand recurring event
                val dayOfWeek = DayOfWeek.valueOf(entry.recurringDay!!.uppercase())
                var current = entry.recurringStart!!
                if (current.isBefore(from)) {
                    current = from
                }
                
                // Align to next occurrence of dayOfWeek
                while (current.dayOfWeek != dayOfWeek) {
                    current = current.plusDays(1)
                }

                while (!current.isAfter(entry.recurringEnd!!) && !current.isAfter(to)) {
                    if (!current.isBefore(entry.recurringStart!!) && !current.isBefore(from)) {
                        result.add(entry.copy(date = current))
                    }
                    current = current.plusWeeks(1)
                }
            } else {
                if (!entry.date.isBefore(from) && !entry.date.isAfter(to)) {
                    result.add(entry)
                }
            }
        }
        return result.sortedBy { it.date.atTime(it.time ?: LocalTime.MIN) }
    }

    private fun parseFile(): List<CalendarEntry> {
        if (!file.exists()) return emptyList()
        val entries = mutableListOf<CalendarEntry>()
        val lines = file.readLines()
        
        var currentBlock: MutableMap<String, String>? = null
        var isRecurringBlock = false

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed == "[EVENT]" -> {
                    currentBlock = mutableMapOf()
                    isRecurringBlock = false
                }
                trimmed == "[/EVENT]" -> {
                    currentBlock?.let { entries.add(mapToEntry(it, false)) }
                    currentBlock = null
                }
                trimmed == "[RECURRING]" -> {
                    currentBlock = mutableMapOf()
                    isRecurringBlock = true
                }
                trimmed == "[/RECURRING]" -> {
                    currentBlock?.let { entries.add(mapToEntry(it, true)) }
                    currentBlock = null
                }
                trimmed.contains(":") -> {
                    val key = trimmed.substringBefore(":").trim()
                    val value = trimmed.substringAfter(":").trim()
                    currentBlock?.put(key, value)
                }
            }
        }
        return entries
    }

    private fun mapToEntry(map: Map<String, String>, isRecurring: Boolean): CalendarEntry {
        val title = map["title"] ?: "Untitled"
        val remind = map["remind"]?.toBoolean() ?: false
        val notifyBefore = map["notify_before"]?.toIntOrNull() ?: 0
        val allDay = map["allday"]?.toBoolean() ?: false
        val timeStr = map["time"]
        val time = if (allDay || timeStr == null) null else LocalTime.parse(timeStr)

        return if (isRecurring) {
            CalendarEntry(
                title = title,
                date = LocalDate.MIN, // Placeholder
                time = time,
                allDay = allDay,
                remind = remind,
                notifyBeforeMinutes = notifyBefore,
                isRecurring = true,
                recurringDay = map["day"],
                recurringStart = LocalDate.parse(map["start"]),
                recurringEnd = LocalDate.parse(map["end"])
            )
        } else {
            val date = LocalDate.parse(map["date"])
            CalendarEntry(
                title = title,
                date = date,
                time = time,
                allDay = allDay,
                remind = remind,
                notifyBeforeMinutes = notifyBefore,
                isRecurring = false
            )
        }
    }

    fun appendEvent(entry: CalendarEntry) {
        val block = StringBuilder()
        block.append("[EVENT]\n")
        block.append("date: ${entry.date}\n")
        block.append("day: ${entry.date.dayOfWeek}\n")
        if (entry.time != null) block.append("time: ${entry.time}\n")
        block.append("allday: ${entry.allDay}\n")
        block.append("title: ${entry.title}\n")
        block.append("remind: ${entry.remind}\n")
        block.append("notify_before: ${entry.notifyBeforeMinutes}\n")
        block.append("[/EVENT]\n\n")
        file.appendText(block.toString())
    }

    fun appendRecurring(entry: CalendarEntry) {
        val block = StringBuilder()
        block.append("[RECURRING]\n")
        block.append("title: ${entry.title}\n")
        block.append("day: ${entry.recurringDay}\n")
        if (entry.time != null) block.append("time: ${entry.time}\n")
        block.append("allday: ${entry.allDay}\n")
        block.append("remind: ${entry.remind}\n")
        block.append("notify_before: ${entry.notifyBeforeMinutes}\n")
        block.append("start: ${entry.recurringStart}\n")
        block.append("end: ${entry.recurringEnd}\n")
        block.append("[/RECURRING]\n\n")
        file.appendText(block.toString())
    }

    fun deleteEntry(title: String, dateOrDay: String): Boolean {
        if (!file.exists()) return false
        val content = file.readText()
        val blocks = findBlocks(content)
        val filteredBlocks = blocks.filterNot { block ->
            val blockTitle = getValue(block, "title")
            val blockDate = getValue(block, "date") ?: getValue(block, "day")
            blockTitle?.contains(title, ignoreCase = true) == true && 
                (blockDate?.equals(dateOrDay, ignoreCase = true) == true || dateOrDay == "any")
        }
        
        if (blocks.size == filteredBlocks.size) return false
        
        file.writeText(filteredBlocks.joinToString("\n\n"))
        return true
    }

    fun editEntry(title: String, dateOrDay: String, updates: Map<String, String>): Boolean {
        if (!file.exists()) return false
        val content = file.readText()
        val blocks = findBlocks(content)
        var edited = false
        val newBlocks = blocks.map { block ->
            val blockTitle = getValue(block, "title")
            val blockDate = getValue(block, "date") ?: getValue(block, "day")
            if (!edited && blockTitle?.contains(title, ignoreCase = true) == true && 
                (blockDate?.equals(dateOrDay, ignoreCase = true) == true || dateOrDay == "any")) {
                edited = true
                applyUpdates(block, updates)
            } else {
                block
            }
        }
        
        if (!edited) return false
        
        file.writeText(newBlocks.joinToString("\n\n"))
        return true
    }

    private fun findBlocks(content: String): List<String> {
        val regex = Regex("\\[(EVENT|RECURRING)].*?\\[/(EVENT|RECURRING)]", RegexOption.DOT_MATCHES_ALL)
        return regex.findAll(content).map { it.value }.toList()
    }

    private fun getValue(block: String, key: String): String? {
        val line = block.lines().find { it.trim().startsWith("$key:") }
        return line?.substringAfter(":")?.trim()
    }

    private fun applyUpdates(block: String, updates: Map<String, String>): String {
        val lines = block.lines().toMutableList()
        updates.forEach { (key, value) ->
            val index = lines.indexOfFirst { it.trim().startsWith("$key:") }
            if (index != -1) {
                lines[index] = "$key: $value"
            } else {
                // Add before end tag
                lines.add(lines.size - 1, "$key: $value")
            }
        }
        // Always recalculate day for events if date changed
        if (updates.containsKey("date") && block.startsWith("[EVENT]")) {
            val date = LocalDate.parse(updates["date"])
            val dayIndex = lines.indexOfFirst { it.trim().startsWith("day:") }
            if (dayIndex != -1) {
                lines[dayIndex] = "day: ${date.dayOfWeek}"
            }
        }
        return lines.joinToString("\n")
    }
}
