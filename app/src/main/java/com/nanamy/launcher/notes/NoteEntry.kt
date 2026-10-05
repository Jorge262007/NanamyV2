package com.nanamy.launcher.notes

import java.time.LocalDateTime

data class NoteEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val createdAt: LocalDateTime = LocalDateTime.now()
)
