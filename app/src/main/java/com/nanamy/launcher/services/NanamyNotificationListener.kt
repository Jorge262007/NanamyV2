package com.nanamy.launcher.services

import android.app.Notification
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.nanamy.launcher.db.MessageEntity
import com.nanamy.launcher.db.NanamyDatabase
import com.nanamy.launcher.messages.ActionStore
import com.nanamy.launcher.messages.ReplyActionHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * NotificationListenerService that captures incoming messages, saves them to Room DB,
 * extracts profile avatar images to disk, and stores reply PendingIntents in ActionStore.
 */
class NanamyNotificationListener : NotificationListenerService() {

    companion object {
        private const val DEBUG_TAG = "NanamyDebug"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val db by lazy { NanamyDatabase.getInstance(applicationContext) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(DEBUG_TAG, "onListenerConnected | Listener connected. Querying active notifications...")
        serviceScope.launch {
            try {
                db.messageDao().cleanupNonMessagingAppMessages()
            } catch (e: Exception) {
                Log.e(DEBUG_TAG, "Failed to cleanup non-WhatsApp messages from DB", e)
            }
        }
        try {
            val activeNotifs = activeNotifications
            val count = activeNotifs?.size ?: 0
            Log.d(DEBUG_TAG, "onListenerConnected | Found $count active notifications")
            if (activeNotifs != null) {
                for (sbn in activeNotifs) {
                    processNotification(sbn, isHistoryRepopulation = true)
                }
            }
            Log.d(DEBUG_TAG, "onListenerConnected | Repopulation complete. ActionStore size: ${ActionStore.getAll().size}")
        } catch (e: Exception) {
            Log.e(DEBUG_TAG, "onListenerConnected | Error repopulating active notifications", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(DEBUG_TAG, "onListenerDisconnected | Listener disconnected. Requesting rebind...")
        try {
            requestRebind(ComponentName(this, NanamyNotificationListener::class.java))
        } catch (e: Exception) {
            Log.e(DEBUG_TAG, "onListenerDisconnected | Error requesting rebind", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        processNotification(sbn, isHistoryRepopulation = false)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        super.onNotificationRemoved(sbn, rankingMap, reason)
        if (sbn == null) return
        val pkg = sbn.packageName
        val title = extractTitle(sbn.notification?.extras ?: Bundle(), sbn.notification)
        val convKey = if (title != null) ActionStore.normalizeKey("$pkg|$title") else "unknown"

        Log.d(DEBUG_TAG, "onNotificationRemoved | pkg=$pkg, sbnKey=${sbn.key}, convKey='$convKey', reason=$reason")

        if (reason == REASON_CANCEL || reason == REASON_CANCEL_ALL) {
            ActionStore.remove(convKey)
            Log.d(DEBUG_TAG, "onNotificationRemoved | Removed ActionStore entry for convKey='$convKey' (reason=$reason)")

            serviceScope.launch {
                try {
                    db.messageDao().markConversationAsReplied(convKey)
                    Log.d(DEBUG_TAG, "onNotificationRemoved | Marked convKey='$convKey' as replied in Room DB")
                } catch (e: Exception) {
                    Log.e(DEBUG_TAG, "onNotificationRemoved | Failed to mark convKey='$convKey' as replied in DB", e)
                }
            }
        }
    }

    private fun isTargetMessagingApp(packageName: String): Boolean {
        // Emergency Patch: Temporarily comment out Google Messages, allow ONLY WhatsApp
        return packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b"
        // || packageName == "com.google.android.apps.messaging"
    }

    private fun processNotification(sbn: StatusBarNotification, isHistoryRepopulation: Boolean) {
        val pkg = sbn.packageName
        if (!isTargetMessagingApp(pkg)) {
            return
        }

        val notification = sbn.notification
        if (notification == null) {
            Log.d(DEBUG_TAG, "processNotification | RETURN EARLY: notification is null (sbn.key=${sbn.key})")
            return
        }

        val extras = notification.extras ?: Bundle()
        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0

        val title = extractTitle(extras, notification)
        val text = extractText(extras, notification)

        val rawConvKey = if (title != null) "$pkg|$title" else ""
        val convKey = ActionStore.normalizeKey(rawConvKey)

        if (ActionStore.isPackageInCooldown(pkg) || ActionStore.isInReplyCooldown(convKey)) {
            Log.d(DEBUG_TAG, "processNotification | IGNORED due to reply cooldown for pkg='$pkg', convKey='$convKey'")
            return
        }

        if (title != null && (title.startsWith("Tú", ignoreCase = true) || title.startsWith("You", ignoreCase = true) || title.equals("Me", ignoreCase = true))) {
            Log.d(DEBUG_TAG, "processNotification | IGNORED outgoing message preview title='$title'")
            return
        }

        if (text != null) {
            if (text.startsWith("Tú: ", ignoreCase = true) || text.startsWith("You: ", ignoreCase = true)) {
                Log.d(DEBUG_TAG, "processNotification | IGNORED outgoing message preview text='$text'")
                return
            }
            if (ActionStore.isTextInRecentSentCache(pkg, text)) {
                Log.d(DEBUG_TAG, "processNotification | IGNORED recent sent text echo for pkg='$pkg', text='$text'")
                return
            }
        }

        Log.d(
            DEBUG_TAG,
            "onNotificationPosted | pkg=$pkg, key=${sbn.key}, id=${sbn.id}, tag=${sbn.tag}, flags=${notification.flags}, isGroupSummary=$isGroupSummary, title='$title' (len=${title?.length ?: 0}), text='$text', convKey='$convKey', isRepopulation=$isHistoryRepopulation"
        )

        // 1. ALWAYS extract and store reply action FIRST if title exists!
        if (!title.isNullOrBlank()) {
            extractAndStoreReplyAction(sbn, convKey, title)
        } else {
            Log.d(DEBUG_TAG, "processNotification | RETURN EARLY: Title is null/blank for pkg=$pkg, key=${sbn.key}")
            return
        }

        // 2. Skip DB insertion if Group Summary
        if (isGroupSummary) {
            Log.d(DEBUG_TAG, "processNotification | SKIP DB INSERTION (Group Summary) for pkg=$pkg, convKey='$convKey'")
            return
        }

        // 3. Skip DB insertion if Text is null/blank
        if (text.isNullOrBlank()) {
            Log.d(DEBUG_TAG, "processNotification | SKIP DB INSERTION: Text is null/blank for pkg=$pkg, convKey='$convKey'")
            return
        }

        // 4. Extract avatar and insert message into Room DB
        val avatarPath = extractAndSaveAvatar(sbn, convKey)
        val timestamp = if (sbn.postTime > 0) sbn.postTime else System.currentTimeMillis()
        val messageEntity = MessageEntity(
            convKey = convKey,
            packageName = pkg,
            senderName = title,
            messageText = text,
            timestamp = timestamp,
            avatarPath = avatarPath,
            isOutgoing = false,
            isReplied = false
        )

        serviceScope.launch {
            try {
                val rowId = db.messageDao().insertMessage(messageEntity)
                if (rowId == -1L) {
                    Log.d(DEBUG_TAG, "processNotification | DB Insert IGNORED (Duplicate message) for convKey='$convKey', timestamp=$timestamp")
                } else {
                    db.messageDao().resetConversationDismissedState(convKey)
                    Log.d(DEBUG_TAG, "processNotification | DB Insert SUCCESS (rowId=$rowId) for convKey='$convKey'")
                }
            } catch (e: Exception) {
                Log.e(DEBUG_TAG, "processNotification | DB Insert EXCEPTION for convKey='$convKey': ${e.message}", e)
            }
        }
    }

    private fun extractTitle(extras: Bundle, notification: Notification? = null): String? {
        var rawTitle: String? = null

        if (notification != null) {
            val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            if (messagingStyle != null) {
                val convTitle = messagingStyle.conversationTitle?.toString()?.trim()
                if (!convTitle.isNullOrBlank()) {
                    rawTitle = convTitle
                } else {
                    val lastMsgPersonName = messagingStyle.messages.lastOrNull()?.person?.name?.toString()?.trim()
                    val userPersonName = messagingStyle.user.name?.toString()?.trim()
                    if (!lastMsgPersonName.isNullOrBlank() && lastMsgPersonName != userPersonName) {
                        rawTitle = lastMsgPersonName
                    }
                }
            }
        }

        if (rawTitle.isNullOrBlank()) {
            val titleCharSeq = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
                ?: extras.getCharSequence(Notification.EXTRA_TITLE)
                ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
            rawTitle = titleCharSeq?.toString()?.trim()
        }

        if (rawTitle == null) return null

        // Strip message count suffixes (e.g., "Lexi 3.0 (2)", "Lexi 3.0 (3 messages)", "Lexi 3.0 (2 nuevos mensajes)")
        val cleanedTitle = rawTitle.replace(Regex("\\s*\\(\\d+(\\s+(messages|mensajes|nuevos))?\\)$", RegexOption.IGNORE_CASE), "").trim()
        return cleanedTitle
    }

    private fun extractText(extras: Bundle, notification: Notification): String? {
        var text = extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)

        if (text == null || text.isBlank()) {
            val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            if (messagingStyle != null && messagingStyle.messages.isNotEmpty()) {
                val lastMsg = messagingStyle.messages.last()
                text = lastMsg.text
            }
        }

        return text?.toString()?.trim()
    }

    private fun extractAndSaveAvatar(sbn: StatusBarNotification, convKey: String): String? {
        return try {
            val notification = sbn.notification
            var icon: Icon? = notification.getLargeIcon()

            if (icon == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val extras = notification.extras
                val senderPerson = extras.getParcelable<Bundle>("android.sender_person")
                if (senderPerson != null) {
                    val personIcon = senderPerson.getParcelable<Icon>("icon")
                    if (personIcon != null) {
                        icon = personIcon
                    }
                }
            }

            if (icon == null) return null

            val drawable = icon.loadDrawable(this) ?: return null
            val bitmap = if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else {
                val b = Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(b)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                b
            }

            val avatarDir = File(filesDir, "avatars")
            if (!avatarDir.exists()) avatarDir.mkdirs()

            val fileName = "avatar_${convKey.hashCode()}.png"
            val file = File(avatarDir, fileName)

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            file.absolutePath
        } catch (e: Exception) {
            Log.e(DEBUG_TAG, "extractAndSaveAvatar | Exception for $convKey: ${e.message}", e)
            null
        }
    }

    private fun extractAndStoreReplyAction(sbn: StatusBarNotification, convKey: String, senderName: String) {
        try {
            val notification = sbn.notification
            var actionsFoundCount = 0
            var wearableActionsFoundCount = 0

            val actions = notification.actions
            if (actions != null) actionsFoundCount = actions.size

            val wearableExtender = NotificationCompat.WearableExtender(notification)
            wearableActionsFoundCount = wearableExtender.actions.size

            Log.d(
                DEBUG_TAG,
                "extractAndStoreReplyAction | convKey='$convKey', n.actionsCount=$actionsFoundCount, wearableActionsCount=$wearableActionsFoundCount"
            )

            // Direct actions in notification
            var replyActionHolder = findReplyActionInNotification(notification, sbn.packageName, convKey, senderName, sbn.postTime)

            // Fallback to WearableExtender actions
            if (replyActionHolder == null) {
                Log.d(DEBUG_TAG, "extractAndStoreReplyAction | Direct reply action not found. Checking WearableExtender actions ($wearableActionsFoundCount)...")
                for (action in wearableExtender.actions) {
                    val remoteInputs = action.remoteInputs
                    val remoteInputsCount = remoteInputs?.size ?: 0
                    val actionTitle = action.title?.toString() ?: "no_title"
                    Log.d(DEBUG_TAG, "WearableExtender Action | title='$actionTitle', remoteInputsCount=$remoteInputsCount")

                    if (!remoteInputs.isNullOrEmpty()) {
                        for (remoteInput in remoteInputs) {
                            Log.d(DEBUG_TAG, "WearableExtender RemoteInput | resultKey='${remoteInput.resultKey}', allowFreeFormInput=${remoteInput.allowFreeFormInput}")
                            if (remoteInput.allowFreeFormInput || remoteInput.resultKey.isNotBlank()) {
                                val pendingIntent = action.actionIntent
                                if (pendingIntent != null) {
                                    replyActionHolder = ReplyActionHolder(
                                        pendingIntent = pendingIntent,
                                        remoteInput = remoteInput,
                                        packageName = sbn.packageName,
                                        convKey = convKey,
                                        senderName = senderName,
                                        timestamp = sbn.postTime
                                    )
                                    Log.d(DEBUG_TAG, "Saved WearableExtender replyActionHolder for convKey='$convKey'")
                                    break
                                } else {
                                    Log.w(DEBUG_TAG, "WearableExtender Action has RemoteInput but pendingIntent is NULL")
                                }
                            }
                        }
                    }
                    if (replyActionHolder != null) break
                }
            }

            if (replyActionHolder != null) {
                ActionStore.put(replyActionHolder)
            } else {
                Log.w(DEBUG_TAG, "extractAndStoreReplyAction | NO valid reply action found for convKey='$convKey'")
            }
        } catch (e: Exception) {
            Log.e(DEBUG_TAG, "extractAndStoreReplyAction | Exception for convKey='$convKey': ${e.message}", e)
        }
    }

    private fun findReplyActionInNotification(
        notification: Notification,
        packageName: String,
        convKey: String,
        senderName: String,
        timestamp: Long
    ): ReplyActionHolder? {
        val actions = notification.actions ?: return null
        var bestCandidate: ReplyActionHolder? = null

        for ((index, action) in actions.withIndex()) {
            val actionTitle = action.title?.toString() ?: "no_title"
            val remoteInputs = action.remoteInputs ?: continue
            val isReplySemantic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && action.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY
            val titleMatchesReply = actionTitle.contains(Regex("(reply|responder|enviar|respuesta)", RegexOption.IGNORE_CASE))

            for (remoteInput in remoteInputs) {
                val isFreeForm = remoteInput.allowFreeFormInput
                val pendingIntent = action.actionIntent ?: continue

                Log.d(
                    DEBUG_TAG,
                    "Direct Action #$index | title='$actionTitle', resultKey='${remoteInput.resultKey}', isFreeForm=$isFreeForm, isReplySemantic=$isReplySemantic, titleMatchesReply=$titleMatchesReply"
                )

                val compatRemoteInput = RemoteInput.Builder(remoteInput.resultKey)
                    .setLabel(remoteInput.label)
                    .setAllowFreeFormInput(isFreeForm)
                    .build()

                val holder = ReplyActionHolder(
                    pendingIntent = pendingIntent,
                    remoteInput = compatRemoteInput,
                    packageName = packageName,
                    convKey = convKey,
                    senderName = senderName,
                    timestamp = timestamp
                )

                // Perfect Match: Freeform input + (Semantic Reply OR title matches "Reply"/"Responder")
                if (isFreeForm && (isReplySemantic || titleMatchesReply)) {
                    Log.d(DEBUG_TAG, "findReplyActionInNotification | PERFECT MATCH Action #$index '$actionTitle'")
                    return holder
                }

                // Backup candidate: any freeform input
                if (isFreeForm && bestCandidate == null) {
                    bestCandidate = holder
                }
            }
        }

        if (bestCandidate != null) {
            Log.d(DEBUG_TAG, "findReplyActionInNotification | Selected best freeform candidate")
            return bestCandidate
        }

        return null
    }
}
