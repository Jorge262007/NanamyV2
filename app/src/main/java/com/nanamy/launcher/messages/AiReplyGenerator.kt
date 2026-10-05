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
 * Accurately detects incoming message language (English vs Spanish) to ensure matching replies.
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

        val detectedLang = detectTextLanguage(incomingText)
        Log.d(TAG, "generateTimeBuyingReply | sender='$senderName', incomingText='$incomingText', detectedLang='$detectedLang'")

        // Try AI generation
        try {
            if (provider == "gemini") {
                val keys = repo.getActiveGeminiKeys()
                if (keys.isNotEmpty()) {
                    for (i in keys.indices) {
                        val keyIndex = (repo.currentGeminiKeyIndex + i) % keys.size
                        val apiKey = keys[keyIndex]
                        val generated = callGeminiApi(apiKey, senderName, incomingText, detectedLang)
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
                    val generated = callGroqApi(apiKey, model, senderName, incomingText, detectedLang)
                    if (generated.isNotBlank()) return generated
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating AI reply via $provider: ${e.message}", e)
        }

        // Fallback time-buying responses matching detected language
        return if (detectedLang == "es") {
            "Sí, te respondo en un rato"
        } else {
            "im a bit busy right now, ill text you later"
        }
    }

    private fun callGeminiApi(apiKey: String, senderName: String, incomingText: String, detectedLang: String): String {
        // IMPORTANT: DO NOT CHANGE THIS MODEL. gemini-3.5-flash-lite is the correct and working version.
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=$apiKey"

        val langDirective = if (detectedLang == "en") {
            "CRITICAL LANGUAGE DIRECTIVE: The incoming message is in ENGLISH. You MUST output your reply in ENGLISH."
        } else {
            "DIRECTIVA CRÍTICA DE IDIOMA: El mensaje entrante está en ESPAÑOL. DEBES responder ÚNICAMENTE en ESPAÑOL."
        }

        val prompt = "Draft a single, ultra-short sentence auto-reply buying time for the user (e.g., 'im a bit busy right now, ill text you later' / 'Sí, te respondo en un rato'). $langDirective Incoming message from $senderName: '$incomingText'. Output ONLY the raw response text, no quotes, no emojis, no explanations."

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

    private fun callGroqApi(apiKey: String, model: String, senderName: String, incomingText: String, detectedLang: String): String {
        val url = "https://api.groq.com/openai/v1/chat/completions"

        val langDirective = if (detectedLang == "en") {
            "CRITICAL LANGUAGE DIRECTIVE: The incoming message is in ENGLISH. You MUST output your reply in ENGLISH."
        } else {
            "DIRECTIVA CRÍTICA DE IDIOMA: El mensaje entrante está en ESPAÑOL. DEBES responder ÚNICAMENTE en ESPAÑOL."
        }

        val systemPrompt = "You are an auto-reply generator. Draft a single, ultra-short sentence buying time for the user (e.g., 'im a bit busy right now, ill text you later' / 'Sí, te respondo en un rato'). $langDirective Output ONLY the raw response text, no quotes, no emojis, no explanations."

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

    fun detectTextLanguage(text: String): String {
        if (text.isBlank()) return "es"

        val englishRegex = Regex(
            "\\b(the|a|an|i|you|he|she|it|we|they|my|your|his|her|its|our|their|this|that|these|those|is|are|am|was|were|be|been|being|have|has|had|do|does|did|will|would|shall|should|can|could|may|might|must|what|where|when|why|how|who|which|and|or|but|if|in|on|at|to|for|with|from|by|about|of|out|up|down|open|play|stop|create|delete|set|get|show|call|text|message|help|now|time|date|weather|music|note|calendar|free|busy|later|meeting|hello|hi|hey|thanks|thank)\\b",
            RegexOption.IGNORE_CASE
        )

        val spanishRegex = Regex(
            "\\b(el|la|los|las|un|una|unos|unas|yo|tú|él|ella|usted|nosotros|vosotros|ellos|ellas|mi|tu|su|nuestro|vuestro|este|esta|esto|estos|estas|ese|esa|eso|esos|esas|aquel|aquella|es|son|soy|eres|somos|está|están|estoy|estás|estamos|fue|fueron|era|éramos|haber|hay|había|hacer|hace|hizo|hacen|tener|tengo|tiene|tienen|que|qué|cómo|cuándo|dónde|por|porque|para|con|sin|sobre|de|del|en|y|o|pero|si|no|sí|hola|adios|gracias|favor|porfavor|abrir|reproducir|detener|crear|borrar|eliminar|poner|llamar|mensaje|música|nota|calendario|clima|hora|fecha|ocupado|libre|luego|rato|respondo)\\b",
            RegexOption.IGNORE_CASE
        )

        val spanishAccentsRegex = Regex("[áéíóúñ¿¡]", RegexOption.IGNORE_CASE)

        val enScore = englishRegex.findAll(text).count()
        var esScore = spanishRegex.findAll(text).count()
        if (spanishAccentsRegex.containsMatchIn(text)) {
            esScore += 3
        }

        return if (enScore > esScore) "en" else "es"
    }
}
