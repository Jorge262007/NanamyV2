package com.nanamy.launcher.messages

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.app.RemoteInput
import com.nanamy.launcher.db.MessageEntity
import com.nanamy.launcher.db.NanamyDatabase

/**
 * Handles sending replies to notifications via RemoteInput and PendingIntent.
 * Returns honest status (Sent or Failed).
 */
sealed class ReplyResult {
    data class Sent(val convKey: String, val text: String, val timestamp: Long) : ReplyResult()
    data class Failed(val convKey: String, val reason: String, val fallbackIntent: Intent?) : ReplyResult()
}

class NotificationReplier(
    private val context: Context,
    private val db: NanamyDatabase
) {

    companion object {
        private const val TAG = "NotificationReplier"
        private const val DEBUG_TAG = "NanamyDebug"
    }

    suspend fun reply(convKey: String, text: String): ReplyResult {
        val normalizedKey = ActionStore.normalizeKey(convKey)
        Log.d(DEBUG_TAG, "NotificationReplier.reply | START | Requested convKey='$convKey', normalizedKey='$normalizedKey', replyText='$text'")

        val actionHolder = ActionStore.get(normalizedKey)
        if (actionHolder == null) {
            Log.w(DEBUG_TAG, "NotificationReplier.reply | FAILED: No action holder found in ActionStore for normalizedKey='$normalizedKey'")
            val fallback = createFallbackIntent(extractPackageName(normalizedKey), text)
            return ReplyResult.Failed(
                convKey = normalizedKey,
                reason = "No active notification reply action found in memory for $normalizedKey",
                fallbackIntent = fallback
            )
        }

        return try {
            Log.d(DEBUG_TAG, "NotificationReplier.reply | Step 1: Found ActionHolder pkg='${actionHolder.packageName}', sender='${actionHolder.senderName}', resultKey='${actionHolder.remoteInput.resultKey}'")

            val intent = Intent().apply {
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
            val bundle = Bundle().apply {
                putCharSequence(actionHolder.remoteInput.resultKey, text)
            }

            // 1. Populate androidx RemoteInput results
            RemoteInput.addResultsToIntent(arrayOf(actionHolder.remoteInput), intent, bundle)
            Log.d(DEBUG_TAG, "NotificationReplier.reply | Step 2: Added androidx RemoteInput results")

            // 2. Populate framework android.app.RemoteInput results for system compatibility
            try {
                val frameworkRemoteInput = android.app.RemoteInput.Builder(actionHolder.remoteInput.resultKey).build()
                android.app.RemoteInput.addResultsToIntent(arrayOf(frameworkRemoteInput), intent, bundle)
                Log.d(DEBUG_TAG, "NotificationReplier.reply | Step 3: Added framework android.app.RemoteInput results")
            } catch (e: Exception) {
                Log.w(DEBUG_TAG, "NotificationReplier.reply | Step 3 Warning: Could not add framework RemoteInput results: ${e.message}")
            }

            Log.d(DEBUG_TAG, "NotificationReplier.reply | Step 4: Calling pendingIntent.send() on actionHolder.pendingIntent...")

            actionHolder.pendingIntent.send(context, 0, intent, { _, _, resultCode, resultData, _ ->
                Log.d(DEBUG_TAG, "NotificationReplier.reply | PendingIntent OnFinished Callback | resultCode=$resultCode, resultData='$resultData'")
            }, null)

            Log.d(DEBUG_TAG, "NotificationReplier.reply | Step 5: pendingIntent.send() triggered successfully!")

            val timestamp = System.currentTimeMillis()
            val outgoingMessage = MessageEntity(
                convKey = normalizedKey,
                packageName = actionHolder.packageName,
                senderName = "Me",
                messageText = text,
                timestamp = timestamp,
                isOutgoing = true,
                isReplied = true
            )
            db.messageDao().insertMessage(outgoingMessage)
            db.messageDao().markConversationAsReplied(normalizedKey)
            ActionStore.setReplyCooldown(normalizedKey, actionHolder.packageName, text, 2500L)

            ReplyResult.Sent(normalizedKey, text, timestamp)
        } catch (e: Exception) {
            Log.e(DEBUG_TAG, "NotificationReplier.reply | FAILED with Exception: ${e.javaClass.simpleName} - ${e.message}", e)
            val fallback = createFallbackIntent(actionHolder.packageName, text)
            ReplyResult.Failed(
                convKey = normalizedKey,
                reason = "${e.javaClass.simpleName}: ${e.message}",
                fallbackIntent = fallback
            )
        }
    }

    private fun extractPackageName(convKey: String): String {
        return convKey.substringBefore('|', "com.whatsapp")
    }

    private fun createFallbackIntent(packageName: String, text: String): Intent? {
        return try {
            if (packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b") {
                Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=${Uri.encode(text)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create fallback intent for $packageName", e)
            null
        }
    }
}
