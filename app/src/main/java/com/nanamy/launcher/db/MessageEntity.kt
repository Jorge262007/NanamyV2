package com.nanamy.launcher.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing an incoming or outgoing notification message.
 * Indexed by convKey, timestamp, and messageText to prevent duplicate insertions
 * when notification listener re-receives published message histories.
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["convKey", "timestamp", "messageText"], unique = true)
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val convKey: String,
    val packageName: String,
    val senderName: String,
    val messageText: String,
    val timestamp: Long,
    val avatarPath: String? = null,
    val isOutgoing: Boolean = false,
    val isReplied: Boolean = false,
    val dismissed: Boolean = false
)
