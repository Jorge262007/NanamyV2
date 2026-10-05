package com.nanamy.launcher.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for message persistence and queries.
 */
@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessagesFlow(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE convKey = :convKey ORDER BY timestamp ASC")
    fun getMessagesForConversationFlow(convKey: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE convKey = :convKey ORDER BY timestamp ASC")
    suspend fun getMessagesForConversation(convKey: String): List<MessageEntity>

    @Query("""
        SELECT * FROM messages 
        WHERE isReplied = 0 AND isOutgoing = 0 AND dismissed = 0
        AND id IN (
            SELECT MAX(id) FROM messages WHERE isReplied = 0 AND isOutgoing = 0 AND dismissed = 0 GROUP BY convKey
        ) 
        ORDER BY timestamp DESC
    """)
    fun getLatestConversationsFlow(): Flow<List<MessageEntity>>

    @Query("""
        SELECT * FROM messages 
        WHERE isReplied = 0 AND isOutgoing = 0 AND dismissed = 0
        AND id IN (
            SELECT MAX(id) FROM messages WHERE isReplied = 0 AND isOutgoing = 0 AND dismissed = 0 GROUP BY convKey
        ) 
        ORDER BY timestamp DESC
    """)
    suspend fun getLatestConversations(): List<MessageEntity>

    @Query("UPDATE messages SET isReplied = 1 WHERE convKey = :convKey")
    suspend fun markConversationAsReplied(convKey: String)

    @Query("UPDATE messages SET dismissed = 1 WHERE convKey = :convKey")
    suspend fun markConversationAsDismissed(convKey: String)

    @Query("UPDATE messages SET dismissed = 0, isReplied = 0 WHERE convKey = :convKey")
    suspend fun resetConversationDismissedState(convKey: String)

    @Query("DELETE FROM messages WHERE packageName NOT IN ('com.whatsapp', 'com.whatsapp.w4b')")
    suspend fun cleanupNonMessagingAppMessages()

    @Query("DELETE FROM messages WHERE convKey = :convKey")
    suspend fun deleteConversation(convKey: String)
}
