package com.nanamy.launcher.messages

import android.app.PendingIntent
import android.util.Log
import androidx.core.app.RemoteInput
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory store for non-serializable notification reply actions (PendingIntent + RemoteInput)
 * and anti-echo reply cooldown tracking to filter out immediate echo notifications after replying.
 * Keyed by normalized unique conversation key ("$packageName|$title").
 */
data class ReplyActionHolder(
    val pendingIntent: PendingIntent,
    val remoteInput: RemoteInput,
    val packageName: String,
    val convKey: String,
    val senderName: String,
    val timestamp: Long
)

object ActionStore {
    private const val DEBUG_TAG = "NanamyDebug"
    private val store = ConcurrentHashMap<String, ReplyActionHolder>()
    private val replyCooldowns = ConcurrentHashMap<String, Long>()
    private val packageCooldowns = ConcurrentHashMap<String, Long>()
    private val recentSentTexts = ConcurrentHashMap<String, Long>()

    fun normalizeKey(key: String): String {
        return java.text.Normalizer.normalize(key, java.text.Normalizer.Form.NFC)
            .replace("\u00A0", " ")
            .replace("\u200E", "")
            .replace("\u200F", "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun put(holder: ReplyActionHolder) {
        val normalizedKey = normalizeKey(holder.convKey)
        val normalizedHolder = holder.copy(convKey = normalizedKey)
        store[normalizedKey] = normalizedHolder
        Log.d(DEBUG_TAG, "ActionStore.put | key='$normalizedKey', rawKey='${holder.convKey}', storeSize=${store.size}")
    }

    fun get(convKey: String): ReplyActionHolder? {
        val normalizedKey = normalizeKey(convKey)
        val holder = store[normalizedKey]
        if (holder != null) {
            Log.d(DEBUG_TAG, "ActionStore.get | FOUND key='$normalizedKey'")
        } else {
            Log.w(DEBUG_TAG, "ActionStore.get | NOT FOUND key='$normalizedKey'. Current keys in ActionStore (${store.size}): ${store.keys}")
            logKeyComparison(normalizedKey)
        }
        return holder
    }

    fun remove(convKey: String) {
        val normalizedKey = normalizeKey(convKey)
        val removed = store.remove(normalizedKey)
        Log.d(DEBUG_TAG, "ActionStore.remove | key='$normalizedKey', existed=${removed != null}, remainingSize=${store.size}")
    }

    fun clear() {
        store.clear()
        replyCooldowns.clear()
        packageCooldowns.clear()
        recentSentTexts.clear()
        Log.d(DEBUG_TAG, "ActionStore.clear | Store cleared")
    }

    fun getAll(): List<ReplyActionHolder> {
        return store.values.toList()
    }

    fun setReplyCooldown(convKey: String, packageName: String, sentText: String, durationMs: Long = 2500L) {
        val normalizedKey = normalizeKey(convKey)
        val now = System.currentTimeMillis()
        val expireTime = now + durationMs

        replyCooldowns[normalizedKey] = expireTime
        packageCooldowns[packageName] = now + 1500L

        val textKey = "$packageName|${normalizeKey(sentText)}"
        recentSentTexts[textKey] = now + 5000L

        Log.d(DEBUG_TAG, "ActionStore.setReplyCooldown | Set cooldown for convKey='$normalizedKey', pkg='$packageName', textKey='$textKey' for $durationMs ms")
    }

    fun isInReplyCooldown(convKey: String): Boolean {
        val normalizedKey = normalizeKey(convKey)
        val expireTime = replyCooldowns[normalizedKey] ?: return false
        val now = System.currentTimeMillis()
        if (now < expireTime) {
            Log.d(DEBUG_TAG, "ActionStore.isInReplyCooldown | TRUE for convKey='$normalizedKey' (${expireTime - now} ms remaining)")
            return true
        }
        replyCooldowns.remove(normalizedKey)
        return false
    }

    fun isPackageInCooldown(packageName: String): Boolean {
        val expireTime = packageCooldowns[packageName] ?: return false
        val now = System.currentTimeMillis()
        if (now < expireTime) {
            Log.d(DEBUG_TAG, "ActionStore.isPackageInCooldown | TRUE for pkg='$packageName' (${expireTime - now} ms remaining)")
            return true
        }
        packageCooldowns.remove(packageName)
        return false
    }

    fun isTextInRecentSentCache(packageName: String, text: String): Boolean {
        val normText = normalizeKey(text)
        val textKey = "$packageName|$normText"
        val expireTime = recentSentTexts[textKey] ?: return false
        val now = System.currentTimeMillis()
        if (now < expireTime) {
            Log.d(DEBUG_TAG, "ActionStore.isTextInRecentSentCache | TRUE for textKey='$textKey'")
            return true
        }
        recentSentTexts.remove(textKey)
        return false
    }

    private fun logKeyComparison(targetKey: String) {
        val targetCodePoints = targetKey.map { "U+%04X".format(it.code) }.joinToString(" ")
        Log.d(DEBUG_TAG, "ActionStore.get | TargetKey Details: '$targetKey' (len=${targetKey.length}, codePoints=[$targetCodePoints])")

        for (existingKey in store.keys) {
            val existingCodePoints = existingKey.map { "U+%04X".format(it.code) }.joinToString(" ")
            Log.d(DEBUG_TAG, "ActionStore.get | Compare against ExistingKey: '$existingKey' (len=${existingKey.length}, codePoints=[$existingCodePoints])")
        }
    }
}
