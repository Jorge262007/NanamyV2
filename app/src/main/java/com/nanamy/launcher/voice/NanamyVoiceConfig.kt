package com.nanamy.launcher.voice

import com.nanamy.launcher.NanamySettingsRepository
import java.util.Locale

object NanamyVoiceConfig {
    
    private lateinit var repository: NanamySettingsRepository

    fun initialize(repo: NanamySettingsRepository) {
        repository = repo
    }

    val groqApiKey: String get() = repository.groqApiKey
    val geminiKeys: List<String> get() = repository.getActiveGeminiKeys()
    var currentGeminiKeyIndex: Int 
        get() = repository.currentGeminiKeyIndex
        set(value) { repository.currentGeminiKeyIndex = value }
    val aiProvider: String get() = repository.aiProvider
    val groqModel: String get() = repository.groqModel
    
    val localLlmEnabled: Boolean get() = repository.localLlmEnabled
    val localLlmModelPath: String get() = repository.localLlmModelPath
    
    fun getFullSystemPrompt(context: android.content.Context? = null, detectedLang: String = "es"): String {
        val basePrompt = "${repository.systemPrompt}\n\nUser Persona:\n${repository.userPersonaPrompt}"
        val now = java.time.LocalDateTime.now()
        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm (EEEE)", java.util.Locale.US)
        val currentDateTimeStr = "\n\nCURRENT DATE & TIME: ${now.format(dateFormatter)}"
        val followDirective = "\n\nFOLLOW-UP TAG RULE: If you ask a question or expect an answer back from the user, append '[Follow]' to the end of your response text. If no answer is expected, do NOT include '[Follow]'."

        val langDirective = if (detectedLang == "en") {
            "\n\nCRITICAL LANGUAGE DIRECTIVE: The user spoke in ENGLISH. You MUST respond ONLY in ENGLISH."
        } else {
            "\n\nDIRECTIVA CRÍTICA DE IDIOMA: El usuario habló en ESPAÑOL. DEBES responder ÚNICAMENTE en ESPAÑOL."
        }

        if (context == null) return basePrompt + currentDateTimeStr + followDirective + langDirective

        return try {
            val db = com.nanamy.launcher.db.NanamyDatabase.getInstance(context)
            val latest = kotlinx.coroutines.runBlocking { db.messageDao().getLatestConversations() }

            val activeContext = if (latest.isEmpty()) {
                "\n\nACTIVE PENDING MESSAGES IN WIDGET: None."
            } else {
                val itemsStr = latest.joinToString("\n") {
                    "- Contact: \"${it.senderName}\", Last message: \"${it.messageText}\", convKey: \"${it.convKey}\""
                }
                "\n\nACTIVE PENDING MESSAGES IN WIDGET:\n$itemsStr\n\nREPLY INSTRUCTIONS:\n- Compare user spoken contact name against active contacts above.\n- Call tool reply_to_message(contact_name_or_key, message_text) with exact convKey or contact name.\n- Unless user dictates a specific custom response, draft a time-buying reply in the conversation's language.\n\nREMINDER INSTRUCTIONS:\n- For reminders, alarms, tasks, or appointments ('reminder', 'remind me', 'recordatorio', 'recuérdame'), ALWAYS call add_calendar_event. NEVER call reply_to_message."
            }
            basePrompt + currentDateTimeStr + followDirective + langDirective + activeContext
        } catch (_: Exception) {
            basePrompt + currentDateTimeStr + followDirective + langDirective
        }
    }

    fun getLocalLlmFullSystemPrompt(): String {
        return "${repository.localLlmSystemPrompt}\n\nUser Persona:\n${repository.localLlmUserPersonaPrompt}"
    }

    fun getNanamyAiFullSystemPrompt(): String {
        return "${repository.systemPrompt}\n\nUser Persona:\n${repository.nanamyAiUserPrompt}"
    }

    // TTS Config
    val ttsVoiceLocaleEs = Locale("es", "ES")
    val ttsVoiceNameEs = "es-es-x-eea-local"
    
    val ttsVoiceLocaleEn = Locale.US
    val ttsVoiceNameEn = "en-us-x-sfg-local"

    val restModeHotword: String get() = repository.restModeHotword
}
