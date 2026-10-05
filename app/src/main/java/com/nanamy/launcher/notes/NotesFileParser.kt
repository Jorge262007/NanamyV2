package com.nanamy.launcher.notes

import android.content.Context
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class NotesFileParser(private val context: Context) {

    private val fileName = "notes.txt"
    private val file: File by lazy { File(context.filesDir, fileName) }
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun getAllNotes(): List<NoteEntry> {
        if (!file.exists()) return emptyList()
        val notes = mutableListOf<NoteEntry>()
        val content = file.readText()
        val blocks = Regex("\\[NOTE].*?\\[/NOTE]", RegexOption.DOT_MATCHES_ALL).findAll(content)

        for (block in blocks) {
            val text = block.value
            val id = getValue(text, "id") ?: java.util.UUID.randomUUID().toString()
            val title = getValue(text, "title") ?: "Untitled"
            val body = getValue(text, "content") ?: ""
            val dateStr = getValue(text, "created")
            val date = try { LocalDateTime.parse(dateStr, formatter) } catch (e: Exception) { LocalDateTime.now() }
            
            notes.add(NoteEntry(id, title, body, date))
        }
        return notes.sortedByDescending { it.createdAt }
    }

    fun saveNote(note: NoteEntry) {
        val notes = getAllNotes().toMutableList()
        val index = notes.indexOfFirst { it.id == note.id }
        if (index != -1) {
            notes[index] = note
        } else {
            notes.add(0, note)
        }
        writeAll(notes)
    }

    fun deleteNote(id: String) {
        val notes = getAllNotes().filterNot { it.id == id }
        writeAll(notes)
    }

    private fun writeAll(notes: List<NoteEntry>) {
        val sb = StringBuilder()
        for (note in notes) {
            sb.append("[NOTE]\n")
            sb.append("id: ${note.id}\n")
            sb.append("title: ${note.title}\n")
            sb.append("created: ${note.createdAt.format(formatter)}\n")
            sb.append("content: ${note.content.replace("\n", "\\n")}\n")
            sb.append("[/NOTE]\n\n")
        }
        file.writeText(sb.toString())
    }

    private fun getValue(block: String, key: String): String? {
        val line = block.lines().find { it.trim().startsWith("$key:") }
        val value = line?.substringAfter(":")?.trim()
        return if (key == "content") value?.replace("\\n", "\n") else value
    }
}
