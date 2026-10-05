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
    
    fun getFullSystemPrompt(context: android.content.Context? = null): String {
        val basePrompt = "${repository.systemPrompt}\n\nUser Persona:\n${repository.userPersonaPrompt}"
        if (context == null) return basePrompt

        return try {
            val db = com.nanamy.launcher.db.NanamyDatabase.getInstance(context)
            val latest = kotlinx.coroutines.runBlocking { db.messageDao().getLatestConversations() }

            val activeContext = if (latest.isEmpty()) {
                "\n\nACTIVE PENDING MESSAGES IN WIDGET: None."
            } else {
                val itemsStr = latest.joinToString("\n") {
                    "- Contact: \"${it.senderName}\", Last message: \"${it.messageText}\", convKey: \"${it.convKey}\""
                }
                "\n\nACTIVE PENDING MESSAGES IN WIDGET:\n$itemsStr\n\nREPLY INSTRUCTIONS:\n- Compare user spoken contact name against active contacts above.\n- Call tool reply_to_message(contact_name_or_key, message_text) with exact convKey or contact name.\n- Unless user dictates a specific custom response, draft a time-buying reply in the conversation's language."
            }
            basePrompt + activeContext
        } catch (_: Exception) {
            basePrompt
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
