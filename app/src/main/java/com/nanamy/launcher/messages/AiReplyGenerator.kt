package com.nanamy.launcher.messages

import android.content.Context
import android.util.Log
import com.nanamy.launcher.NanamyApplication
import com.nanamy.launcher.db.MessageEntity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Generates automated time-buying auto-replies using LLM (Gemini/Groq) with fallback.
 */
object AiReplyGenerator {

    private const val TAG = "AiReplyGenerator"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun generateTimeBuyingReply(context: Context, history: List<MessageEntity>): String {
        val lastMsg = history.lastOrNull { !it.isOutgoing } ?: history.lastOrNull()
        val incomingText = lastMsg?.messageText ?: ""
        val senderName = lastMsg?.senderName ?: "Friend"

        val repo = (context.applicationContext as NanamyApplication).settingsRepository
        val provider = repo.aiProvider

        val isSpanish = isSpanishText(incomingText)

        // Try AI generation
        try {
            if (provider == "gemini") {
                val keys = repo.getActiveGeminiKeys()
                if (keys.isNotEmpty()) {
                    for (i in keys.indices) {
                        val keyIndex = (repo.currentGeminiKeyIndex + i) % keys.size
                        val apiKey = keys[keyIndex]
                        val generated = callGeminiApi(apiKey, senderName, incomingText)
                        if (generated.isNotBlank()) {
                            repo.currentGeminiKeyIndex = keyIndex
                            return generated
                        }
                    }
                }
            } else if (provider == "groq") {
                val apiKey = repo.groqApiKey
                val model = repo.groqModel
                if (apiKey.isNotBlank()) {
                    val generated = callGroqApi(apiKey, model, senderName, incomingText)
                    if (generated.isNotBlank()) return generated
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating AI reply via $provider: ${e.message}", e)
        }

        // Fallback time-buying responses
        return if (isSpanish) {
            "Sí, te respondo en un rato"
        } else {
            "im a bit busy right now, ill text you later"
        }
    }

    private fun callGeminiApi(apiKey: String, senderName: String, incomingText: String): String {
        // IMPORTANT: DO NOT CHANGE THIS MODEL. gemini-3.5-flash-lite is the correct and working version.
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=$apiKey"

        val prompt = "Draft a single, ultra-short sentence auto-reply buying time for the user (e.g., 'im a bit busy right now, ill text you later' / 'Sí, te respondo en un rato'). Match the language of the incoming message from $senderName: '$incomingText'. Output ONLY the raw response text, no quotes, no emojis, no explanations."

        val payload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: return ""

        if (!response.isSuccessful) {
            Log.e(TAG, "Gemini API error ${response.code}: $body")
            return ""
        }

        val json = JSONObject(body)
        val text = json.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .trim()
            .replace("\"", "")

        return text
    }

    private fun callGroqApi(apiKey: String, model: String, senderName: String, incomingText: String): String {
        val url = "https://api.groq.com/openai/v1/chat/completions"

        val systemPrompt = "You are an auto-reply generator. Draft a single, ultra-short sentence buying time for the user (e.g., 'im a bit busy right now, ill text you later' / 'Sí, te respondo en un rato'). Match the language of the incoming message. Output ONLY the raw response text, no quotes, no emojis, no explanations."

        val payload = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Incoming message from $senderName: '$incomingText'")
                })
            })
            put("max_tokens", 60)
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: return ""

        if (!response.isSuccessful) {
            Log.e(TAG, "Groq API error ${response.code}: $body")
            return ""
        }

        val json = JSONObject(body)
        val text = json.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
            .replace("\"", "")

        return text
    }

    private fun isSpanishText(text: String): Boolean {
        return text.contains(Regex("[áéíóúñ¿¡a-z]", RegexOption.IGNORE_CASE))
    }
}
