package com.nanamy.launcher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.core.content.edit
import org.json.JSONArray

class NanamySettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "nanamy_settings"

        const val KEY_GROQ_API_KEY = "groq_api_key"
        const val KEY_GEMINI_KEYS_JSON = "gemini_keys_json"
        const val KEY_AI_PROVIDER = "ai_provider"
        const val KEY_CURRENT_GEMINI_KEY_INDEX = "current_gemini_key_index"
        const val KEY_GROQ_MODEL = "groq_model"
        const val KEY_SYSTEM_PROMPT = "system_prompt"
        const val KEY_USER_PERSONA_PROMPT = "user_persona_prompt"

        const val KEY_TTS_SPEECH_RATE = "tts_speech_rate"
        const val KEY_TTS_PITCH = "tts_pitch"

        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_EYES_COLOR = "eyes_color"

        const val KEY_WIDGET_MUSIC_ENABLED = "widget_music_enabled"
        const val KEY_WIDGET_WEATHER_ENABLED = "widget_weather_enabled"
        const val KEY_WIDGET_NOTES_ENABLED = "widget_notes_enabled"
        const val KEY_WIDGET_CALENDAR_ENABLED = "widget_calendar_enabled"
        const val KEY_WIDGET_MESSAGES_ENABLED = "widget_messages_enabled"

        const val KEY_LOCAL_LLM_ENABLED = "local_llm_enabled"
        const val KEY_LOCAL_LLM_MODEL_PATH = "local_llm_model_path"
        const val KEY_LOCAL_LLM_SYSTEM_PROMPT = "local_llm_system_prompt"
        const val KEY_LOCAL_LLM_USER_PERSONA_PROMPT = "local_llm_user_persona_prompt"
        const val KEY_NANAMY_AI_USER_PROMPT = "nanamy_ai_user_prompt"
        const val KEY_REST_MODE_HOTWORD = "rest_mode_hotword"
        const val KEY_REST_MODE_VOLUME = "rest_mode_volume"
        const val KEY_REST_MODE_HANDS_FREE = "rest_mode_hands_free"
        const val KEY_WIDGET_ORDER_JSON = "widget_order_json"

        private val DEFAULT_SYSTEM_PROMPT = """
            You are Nanamy, a highly efficient and direct assistant.
            
            RULES:
            - Be extremely concise and brief.
            - NO small talk. NO repetition.
            - NO emojis. NO thinking tags.
            - FOLLOW-UP QUESTIONS: If you ask a question or expect an answer back from the user (e.g., asking for clarification, missing event time or details), YOU MUST INCLUDE `[Follow]` at the end of your message. Do NOT include `[Follow]` if you are stating a final response or completing an action.
            - REMINDERS & CALENDAR: When the user asks for a reminder, alarm, event, task, schedule, or appointment (e.g., "reminder", "remind me", "set a reminder", "set an alarm", "schedule", "notify me", "appointment", "recordatorio", "recuérdame", "avísame", "pon una alarma", "cita", "agenda"), YOU MUST CALL `add_calendar_event` or `add_recurring_event`. Infer title, date (default today if unspecified), and time (e.g., "at 5" / "a las 5" -> "17:00"). NEVER confuse reminders with messaging tools.
            - MESSAGE REPLIES: Call `reply_to_message` ONLY when the user explicitly commands to reply/answer an incoming text message from a contact (e.g., "reply to John", "text Pedro back", "responde a Pedro"). NEVER call `reply_to_message` for reminders or calendar tasks.
            
            EXAMPLES:
            User: Programame una cita
            AI: ¿A qué hora quieres programar la cita? [Follow]
            User: Remind me at 5 to call the school
            AI Tool Call: add_calendar_event(date="2026-10-04", time="17:00", title="Call the school")
            User: Reply to John that I will call him later
            AI Tool Call: reply_to_message(contact_name_or_key="John", message_text="I'll call you later")
        """.trimIndent()

        private val DEFAULT_LOCAL_LLM_SYSTEM_PROMPT = """
            You are Nanamy. Ultra-concise mode.
            - Direct, accurate and short answers only.
            - NO greetings unless necessary. NO emojis.
            
            EXAMPLES:
            User: Hola
            AI: Hola humano, ¿cómo te encuentras?
            User: ¿De qué color es el cielo?
            AI: El cielo es de color azul.
        """.trimIndent()
    }

    var groqApiKey: String
        get() = prefs.getString(KEY_GROQ_API_KEY, "") ?: ""
        set(value) = prefs.edit { putString(KEY_GROQ_API_KEY, value) }

    var geminiKeysJson: String
        get() = prefs.getString(KEY_GEMINI_KEYS_JSON, "[]") ?: "[]"
        set(value) = prefs.edit { putString(KEY_GEMINI_KEYS_JSON, value) }

    fun getActiveGeminiKeys(): List<String> {
        val list = mutableListOf<String>()
        try {
            val arr = JSONArray(geminiKeysJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.optBoolean("inUse", false)) {
                    list.add(obj.getString("key"))
                }
            }
        } catch (e: Exception) {}
        return list
    }

    var aiProvider: String
        get() = prefs.getString(KEY_AI_PROVIDER, "gemini") ?: "gemini"
        set(value) = prefs.edit { putString(KEY_AI_PROVIDER, value) }

    var currentGeminiKeyIndex: Int
        get() = prefs.getInt(KEY_CURRENT_GEMINI_KEY_INDEX, 0)
        set(value) = prefs.edit { putInt(KEY_CURRENT_GEMINI_KEY_INDEX, value) }

    var groqModel: String
        get() = prefs.getString(KEY_GROQ_MODEL, "qwen/qwen3.6-27b") ?: "qwen/qwen3.6-27b"
        set(value) = prefs.edit { putString(KEY_GROQ_MODEL, value) }

    var systemPrompt: String
        get() = prefs.getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT
        set(value) = prefs.edit { putString(KEY_SYSTEM_PROMPT, value) }

    var userPersonaPrompt: String
        get() = prefs.getString(KEY_USER_PERSONA_PROMPT, "You are Nanamy, a warm and direct assistant.") ?: "You are Nanamy, a warm and direct assistant."
        set(value) = prefs.edit { putString(KEY_USER_PERSONA_PROMPT, value) }

    var ttsSpeechRate: Float
        get() = prefs.getFloat(KEY_TTS_SPEECH_RATE, 1.0f)
        set(value) = prefs.edit { putFloat(KEY_TTS_SPEECH_RATE, value) }

    var ttsPitch: Float
        get() = prefs.getFloat(KEY_TTS_PITCH, 1.0f)
        set(value) = prefs.edit { putFloat(KEY_TTS_PITCH, value) }

    var themeMode: Int
        get() = prefs.getInt(KEY_THEME_MODE, 0)
        set(value) = prefs.edit { putInt(KEY_THEME_MODE, value) }

    var eyesColor: Int
        get() = prefs.getInt(KEY_EYES_COLOR, Color.WHITE)
        set(value) = prefs.edit { putInt(KEY_EYES_COLOR, value) }

    var widgetMusicEnabled: Boolean
        get() = prefs.getBoolean(KEY_WIDGET_MUSIC_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_WIDGET_MUSIC_ENABLED, value) }

    var widgetWeatherEnabled: Boolean
        get() = prefs.getBoolean(KEY_WIDGET_WEATHER_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_WIDGET_WEATHER_ENABLED, value) }

    var widgetNotesEnabled: Boolean
        get() = prefs.getBoolean(KEY_WIDGET_NOTES_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_WIDGET_NOTES_ENABLED, value) }

    var widgetCalendarEnabled: Boolean
        get() = prefs.getBoolean(KEY_WIDGET_CALENDAR_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_WIDGET_CALENDAR_ENABLED, value) }

    var widgetMessagesEnabled: Boolean
        get() = prefs.getBoolean(KEY_WIDGET_MESSAGES_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_WIDGET_MESSAGES_ENABLED, value) }

    var localLlmEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOCAL_LLM_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_LOCAL_LLM_ENABLED, value) }

    var localLlmModelPath: String
        get() = prefs.getString(KEY_LOCAL_LLM_MODEL_PATH, "") ?: ""
        set(value) = prefs.edit { putString(KEY_LOCAL_LLM_MODEL_PATH, value) }

    var localLlmSystemPrompt: String
        get() = prefs.getString(KEY_LOCAL_LLM_SYSTEM_PROMPT, DEFAULT_LOCAL_LLM_SYSTEM_PROMPT) ?: DEFAULT_LOCAL_LLM_SYSTEM_PROMPT
        set(value) = prefs.edit { putString(KEY_LOCAL_LLM_SYSTEM_PROMPT, value) }

    var localLlmUserPersonaPrompt: String
        get() = prefs.getString(KEY_LOCAL_LLM_USER_PERSONA_PROMPT, "You are Nanamy, offline mode.") ?: "You are Nanamy, offline mode."
        set(value) = prefs.edit { putString(KEY_LOCAL_LLM_USER_PERSONA_PROMPT, value) }

    var nanamyAiUserPrompt: String
        get() = prefs.getString(KEY_NANAMY_AI_USER_PROMPT, "You are Nanamy, a helpful AI assistant integrated into the OS desktop. Be technical, direct and efficient. DO NOT use emojis.") ?: "You are Nanamy, a helpful AI assistant integrated into the OS desktop. Be technical, direct and efficient. DO NOT use emojis."
        set(value) = prefs.edit { putString(KEY_NANAMY_AI_USER_PROMPT, value) }

    var restModeHotword: String
        get() = prefs.getString(KEY_REST_MODE_HOTWORD, "Nanamy") ?: "Nanamy"
        set(value) = prefs.edit { putString(KEY_REST_MODE_HOTWORD, value) }

    var restModeVolume: Int
        get() = prefs.getInt(KEY_REST_MODE_VOLUME, 70)
        set(value) = prefs.edit { putInt(KEY_REST_MODE_VOLUME, value) }

    var restModeHandsFreeEnabled: Boolean
        get() = prefs.getBoolean(KEY_REST_MODE_HANDS_FREE, true)
        set(value) = prefs.edit { putBoolean(KEY_REST_MODE_HANDS_FREE, value) }

    var widgetOrderJson: String
        get() = prefs.getString(KEY_WIDGET_ORDER_JSON, "[\"MUSIC\", \"WEATHER\", \"CALENDAR\", \"NOTES\", \"MESSAGES\"]") 
            ?: "[\"MUSIC\", \"WEATHER\", \"CALENDAR\", \"NOTES\", \"MESSAGES\"]"
        set(value) = prefs.edit { putString(KEY_WIDGET_ORDER_JSON, value) }
}
