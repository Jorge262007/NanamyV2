package com.nanamy.launcher.voice

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import com.nanamy.launcher.calendar.CalendarAlarmScheduler
import com.nanamy.launcher.calendar.CalendarEntry
import com.nanamy.launcher.calendar.CalendarFileParser
import com.nanamy.launcher.calendar.CalendarViewModel
import com.nanamy.launcher.db.MessageEntity
import com.nanamy.launcher.db.NanamyDatabase
import com.nanamy.launcher.messages.NotificationReplier
import com.nanamy.launcher.messages.ReplyResult
import com.nanamy.launcher.notes.*
import com.nanamy.launcher.eyes.NanamyState
import com.nanamy.launcher.localllm.LocalLlmEngine
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Manages the voice assistant flow with Tool Use support: STT -> Gemini/Groq API (Tools) -> TTS.
 * Single-stage tool calling (no router).
 */
class VoiceAssistantManager(
    private val context: Context,
    private val calendarViewModel: CalendarViewModel,
    private val notesViewModel: NotesViewModel,
    private val onStateChanged: (NanamyState) -> Unit
) : RecognitionListener, TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "NanamyVoice"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val TOOLS = JSONArray().apply {
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "get_system_info")
                    put("description", "Get current device status such as battery, storage, ram, network, or datetime.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("category", JSONObject().apply {
                                put("type", "string")
                                put("enum", JSONArray(listOf("storage", "battery", "ram", "network", "datetime", "all")))
                            })
                        })
                        put("required", JSONArray(listOf("category")))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "uninstall_app")
                    put("description", "Request to uninstall a user application by its visible name.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("app_name", JSONObject().apply { put("type", "string") })
                        })
                        put("required", JSONArray(listOf("app_name")))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "explore_path")
                    put("description", "List files and folders inside a specific directory path. Use generic names: pictures, downloads, music, docs, home.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("path", JSONObject().apply { put("type", "string") })
                        })
                        put("required", JSONArray(listOf("path")))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "delete_file")
                    put("description", "Permanently delete a file or empty folder at the specified path.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("path", JSONObject().apply { put("type", "string") })
                        })
                        put("required", JSONArray(listOf("path")))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "open_app")
                    put("description", "Open an application by its visible name.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("app_name", JSONObject().apply { put("type", "string") })
                        })
                        put("required", JSONArray(listOf("app_name")))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "get_calendar_events")
                    put("description", "Get events between two dates (ISO 8601).")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("date_from", JSONObject().apply { put("type", "string") })
                            put("date_to", JSONObject().apply { put("type", "string") })
                        })
                        put("required", JSONArray(listOf("date_from", "date_to")))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "add_calendar_event")
                    put("description", "Add a one-time event.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("date", JSONObject().apply { put("type", "string") })
                            put("time", JSONObject().apply { put("type", "string") })
                            put("title", JSONObject().apply { 
                                put("type", "string") 
                                put("description", "A concrete, specific title. NEVER use generic words like 'Appointment', 'Meeting', or 'Event'. If the user didn't provide a specific name, YOU MUST ASK them before calling this tool.")
                            })
                            put("remind", JSONObject().apply { 
                                put("type", "boolean")
                                put("description", "Whether to show an exact alarm. Default TRUE. Only set to FALSE if user specifically asks NOT to have an alarm.")
                            })
                            put("notify_before", JSONObject().apply { 
                                put("type", "integer")
                                put("description", "Minutes before the event. Default 10. Only set to 0 if user specifically asks for NO early notification.")
                            })
                            put("allday", JSONObject().apply { put("type", "boolean") })
                        })
                        put("required", JSONArray(listOf("date", "title")))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "add_recurring_event")
                    put("description", "Add a weekly recurring event.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("title", JSONObject().apply { 
                                put("type", "string") 
                                put("description", "A concrete, specific title. NEVER use generic words like 'Appointment', 'Meeting', or 'Event'. If the user didn't provide a specific name, YOU MUST ASK them before calling this tool.")
                            })
                            put("day", JSONObject().apply { put("type", "string") })
                            put("time", JSONObject().apply { put("type", "string") })
                            put("start", JSONObject().apply { put("type", "string") })
                            put("end", JSONObject().apply { put("type", "string") })
                            put("remind", JSONObject().apply { 
                                put("type", "boolean")
                                put("description", "Whether to show an exact alarm. Default TRUE. Only set to FALSE if user specifically asks NOT to have an alarm.")
                            })
                            put("notify_before", JSONObject().apply { 
                                put("type", "integer")
                                put("description", "Minutes before the event. Default 10. Only set to 0 if user specifically asks for NO early notification.")
                            })
                            put("allday", JSONObject().apply { put("type", "boolean") })
                        })
                        put("required", JSONArray(listOf("title", "day", "start", "end")))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "delete_calendar_event")
                    put("description", "Delete an event by title and optional date/day.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("title", JSONObject().apply { 
                                put("type", "string") 
                                put("description", "A concrete, specific title. NEVER use generic words like 'Appointment', 'Meeting', or 'Event'. If the user didn't provide a specific name, YOU MUST ASK them before calling this tool.")
                            })
                            put("date_or_day", JSONObject().apply { put("type", "string") })
                        })
                        put("required", JSONArray(listOf("title")))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "edit_calendar_event")
                    put("description", "Edit an existing event. Provide only the fields to change.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("title", JSONObject().apply { 
                                put("type", "string") 
                                put("description", "A concrete, specific title. NEVER use generic words like 'Appointment', 'Meeting', or 'Event'. If the user didn't provide a specific name, YOU MUST ASK them before calling this tool.")
                            })
                            put("date_or_day", JSONObject().apply { put("type", "string") })
                            put("new_title", JSONObject().apply { put("type", "string") })
                            put("date", JSONObject().apply { put("type", "string") })
                            put("time", JSONObject().apply { put("type", "string") })
                            put("remind", JSONObject().apply { 
                                put("type", "boolean")
                                put("description", "Whether to show an exact alarm. Default TRUE. Only set to FALSE if user specifically asks NOT to have an alarm.")
                            })
                            put("notify_before", JSONObject().apply { 
                                put("type", "integer")
                                put("description", "Minutes before the event. Default 10. Only set to 0 if user specifically asks for NO early notification.")
                            })
                            put("allday", JSONObject().apply { put("type", "boolean") })
                            put("day", JSONObject().apply { put("type", "string") })
                            put("start", JSONObject().apply { put("type", "string") })
                            put("end", JSONObject().apply { put("type", "string") })
                        })
                        put("required", JSONArray(listOf("title")))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "list_pending_messages")
                    put("description", "List recent pending or received message conversations with sender names, last message text, and timestamps.")
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "read_conversation")
                    put("description", "Read full message history for a specific conversation or contact.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("contact_name_or_key", JSONObject().apply { put("type", "string"); put("description", "Contact name or conversation key.") })
                        })
                        put("required", JSONArray(listOf("contact_name_or_key")))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "reply_to_message")
                    put("description", "Send a reply to a specific contact or conversation. Call ONLY when explicitly commanded by user voice command.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("contact_name_or_key", JSONObject().apply { put("type", "string"); put("description", "Contact name or conversation key.") })
                            put("message_text", JSONObject().apply { put("type", "string"); put("description", "Optional custom message text. If omitted, default to a time-buying response in the conversation language.") })
                        })
                        put("required", JSONArray(listOf("contact_name_or_key")))
                    })
                })
            })
        }

        private val GEMINI_TOOLS = JSONArray().apply {
            put(JSONObject().apply {
                val funcDeclarations = JSONArray()
                funcDeclarations.put(JSONObject().apply {
                    put("name", "get_system_info")
                    put("description", "Get current device status such as battery, storage, ram, network, or datetime.")
                    put("parameters", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            put("category", JSONObject().apply {
                                put("type", "STRING")
                                put("description", "Category: storage, battery, ram, network, datetime, all")
                            })
                        })
                        put("required", JSONArray(listOf("category")))
                    })
                })
                funcDeclarations.put(JSONObject().apply {
                    put("name", "uninstall_app")
                    put("description", "Request to uninstall a user application by its visible name.")
                    put("parameters", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            put("app_name", JSONObject().apply { put("type", "STRING") })
                        })
                        put("required", JSONArray(listOf("app_name")))
                    })
                })
                funcDeclarations.put(JSONObject().apply {
                    put("name", "explore_path")
                    put("description", "List files and folders inside a specific directory path. Use generic names: pictures, downloads, music, docs, home.")
                    put("parameters", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            put("path", JSONObject().apply { put("type", "STRING") })
                        })
                        put("required", JSONArray(listOf("path")))
                    })
                })
                funcDeclarations.put(JSONObject().apply {
                    put("name", "delete_file")
                    put("description", "Permanently delete a file or empty folder at the specified path.")
                    put("parameters", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            put("path", JSONObject().apply { put("type", "STRING") })
                        })
                        put("required", JSONArray(listOf("path")))
                    })
                })
                funcDeclarations.put(JSONObject().apply {
                    put("name", "open_app")
                    put("description", "Open an application by its visible name.")
                    put("parameters", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            put("app_name", JSONObject().apply { put("type", "STRING") })
                        })
                        put("required", JSONArray(listOf("app_name")))
                    })
                })
                funcDeclarations.put(JSONObject().apply {
                    put("name", "get_calendar_events")
                    put("description", "Get events between two dates (ISO 8601).")
                    put("parameters", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            put("date_from", JSONObject().apply { put("type", "STRING") })
                            put("date_to", JSONObject().apply { put("type", "STRING") })
                        })
                        put("required", JSONArray(listOf("date_from", "date_to")))
                    })
                })
                funcDeclarations.put(JSONObject().apply {
                    put("name", "add_calendar_event")
                    put("description", "Add a one-time event.")
                    put("parameters", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            put("date", JSONObject().apply { put("type", "STRING") })
                            put("time", JSONObject().apply { put("type", "STRING") })
                            put("title", JSONObject().apply { 
                                put("type", "STRING") 
                                put("description", "A concrete title. NEVER use generic placeholders like 'Appointment', 'Event' or 'Meeting', even with adjectives like 'Urgent' or 'New'. If vague, YOU MUST ASK for details first.")
                            })
                            put("remind", JSONObject().apply { 
                                put("type", "BOOLEAN")
                                put("description", "Whether to show an exact alarm. Default TRUE. Only set to FALSE if user specifically asks NOT to have an alarm.")
                            })
                            put("notify_before", JSONObject().apply { 
                                put("type", "INTEGER")
                                put("description", "Minutes before the event. Default 10. Only set to 0 if user specifically asks for NO early notification.")
                            })
                            put("allday", JSONObject().apply { put("type", "BOOLEAN") })
                        })
                        put("required", JSONArray(listOf("date", "title")))
                    })
                })
                funcDeclarations.put(JSONObject().apply {
                    put("name", "add_recurring_event")
                    put("description", "Add a weekly recurring event.")
                    put("parameters", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            put("title", JSONObject().apply { 
                                put("type", "STRING") 
                                put("description", "A concrete title. NEVER use generic placeholders like 'Appointment', 'Event' or 'Meeting', even with adjectives like 'Urgent' or 'New'. If vague, YOU MUST ASK for details first.")
                            })
                            put("day", JSONObject().apply { put("type", "STRING") })
                            put("time", JSONObject().apply { put("type", "STRING") })
                            put("start", JSONObject().apply { put("type", "STRING") })
                            put("end", JSONObject().apply { put("type", "STRING") })
                            put("remind", JSONObject().apply { 
                                put("type", "BOOLEAN")
                                put("description", "Whether to show an exact alarm. Default TRUE. Only set to FALSE if user specifically asks NOT to have an alarm.")
                            })
                            put("notify_before", JSONObject().apply { 
                                put("type", "INTEGER")
                                put("description", "Minutes before the event. Default 10. Only set to 0 if user specifically asks for NO early notification.")
                            })
                            put("allday", JSONObject().apply { put("type", "BOOLEAN") })
                        })
                        put("required", JSONArray(listOf("title", "day", "start", "end")))
                    })
                })
                funcDeclarations.put(JSONObject().apply {
                    put("name", "delete_calendar_event")
                    put("description", "Delete an event by title and optional date/day.")
                    put("parameters", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            put("title", JSONObject().apply { put("type", "STRING") })
                            put("date_or_day", JSONObject().apply { put("type", "STRING") })
                        })
                        put("required", JSONArray(listOf("title")))
                    })
                })
                funcDeclarations.put(JSONObject().apply {
                    put("name", "edit_calendar_event")
                    put("description", "Edit an existing event. Provide only the fields to change.")
                    put("parameters", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            put("title", JSONObject().apply { put("type", "STRING") })
                            put("date_or_day", JSONObject().apply { put("type", "STRING") })
                            put("new_title", JSONObject().apply { put("type", "STRING") })
                            put("date", JSONObject().apply { put("type", "STRING") })
                            put("time", JSONObject().apply { put("type", "STRING") })
                            put("remind", JSONObject().apply { 
                                put("type", "BOOLEAN")
                                put("description", "Whether to show an exact alarm. Default TRUE. Only set to FALSE if user specifically asks NOT to have an alarm.")
                            })
                            put("notify_before", JSONObject().apply { 
                                put("type", "INTEGER")
                                put("description", "Minutes before the event. Default 10. Only set to 0 if user specifically asks for NO early notification.")
                            })
                            put("allday", JSONObject().apply { put("type", "BOOLEAN") })
                            put("day", JSONObject().apply { put("type", "STRING") })
                            put("start", JSONObject().apply { put("type", "STRING") })
                            put("end", JSONObject().apply { put("type", "STRING") })
                        })
                        put("required", JSONArray(listOf("title")))
                    })
                })
                funcDeclarations.put(JSONObject().apply {
                    put("name", "get_notes")
                    put("description", "Get all personal notes.")
                })
                funcDeclarations.put(JSONObject().apply {
                    put("name", "add_note")
                    put("description", "Create a new note.")
                    put("parameters", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            put("title", JSONObject().apply { put("type", "STRING"); put("description", "Optional title. If missing, generate one from content.") })
                            put("content", JSONObject().apply { put("type", "STRING"); put("description", "The note body text.") })
                        })
                        put("required", JSONArray(listOf("content")))
                    })
                })
                funcDeclarations.put(JSONObject().apply {
                    put("name", "delete_note")
                    put("description", "Delete a note by title matching.")
                    put("parameters", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            put("title", JSONObject().apply { put("type", "STRING") })
                        })
                        put("required", JSONArray(listOf("title")))
                    })
                })
                funcDeclarations.put(JSONObject().apply {
                    put("name", "list_pending_messages")
                    put("description", "List recent pending or received message conversations with sender names, last message text, and timestamps.")
                })
                funcDeclarations.put(JSONObject().apply {
                    put("name", "read_conversation")
                    put("description", "Read full message history for a specific conversation or contact.")
                    put("parameters", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            put("contact_name_or_key", JSONObject().apply { put("type", "STRING"); put("description", "Contact name or conversation key.") })
                        })
                        put("required", JSONArray(listOf("contact_name_or_key")))
                    })
                })
                funcDeclarations.put(JSONObject().apply {
                    put("name", "reply_to_message")
                    put("description", "Send a reply to a specific contact or conversation. Call ONLY when explicitly commanded by user voice command.")
                    put("parameters", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            put("contact_name_or_key", JSONObject().apply { put("type", "STRING"); put("description", "Contact name or conversation key.") })
                            put("message_text", JSONObject().apply { put("type", "STRING"); put("description", "Optional custom message text. If omitted, default to a time-buying response in the conversation language.") })
                        })
                        put("required", JSONArray(listOf("contact_name_or_key")))
                    })
                })
                put("function_declarations", funcDeclarations)
            })
        }
    }

    private val settingsRepository by lazy {
        (context.applicationContext as com.nanamy.launcher.NanamyApplication).settingsRepository
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager

    private var speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
        setRecognitionListener(this@VoiceAssistantManager)
    }

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val calendarParser = CalendarFileParser(context)
    private val calendarScheduler = CalendarAlarmScheduler(context)
    private val notesParser = NotesFileParser(context)

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var currentTtsLanguage = "es"
    private var detectedInputLanguage = "es"
    private var isListening = false
    private var explorePathCallCount = 0

    private var isWaitingForCommand = false

    private var preloadJob: Job? = null
    private val conversationHistory = LinkedList<JSONObject>()
    private val MAX_HISTORY_TURNS = 2

    init {
        Log.d(TAG, "VoiceAssistantManager initialized")
        setupTtsListener()
    }

    private fun setupTtsListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                onStateChanged(NanamyState.SPEAKING)
            }

            override fun onDone(utteranceId: String?) {
                context.mainExecutor.execute { 
                    onStateChanged(NanamyState.IDLE)
                }
            }

            override fun onError(utteranceId: String?) {
                context.mainExecutor.execute { onStateChanged(NanamyState.IDLE) }
            }
        })
    }

    fun startListening() {
        Log.d(TAG, "startListening() | isWaitingForCommand=$isWaitingForCommand, isListening=$isListening")
        if (isListening) return
        
        try {
            tts?.stop()
            // Standard recognizer is safer across all Android 12 implementations
            speechRecognizer.destroy() 
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(this@VoiceAssistantManager)
            }
        } catch(e: Exception) {
            Log.e(TAG, "Error recreating SpeechRecognizer: ${e.message}")
        }
        
        isListening = true
        explorePathCallCount = 0
        onStateChanged(NanamyState.LISTENING)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-ES")
            
            val additionalLanguages = arrayOf("en-US")
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", additionalLanguages)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            
            // Standard silence timeouts for push-to-talk hold
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000L)
        }
        
        try {
            Log.d(TAG, "Calling speechRecognizer.startListening()")
            speechRecognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting SpeechRecognizer: ${e.message}")
            isListening = false
            onStateChanged(NanamyState.IDLE)
        }
    }

    fun stopListening() {
        Log.d(TAG, "stopListening() called | isListening=$isListening")
        if (!isListening) return
        
        isListening = false
        speechRecognizer.stopListening()
    }

    private fun processUserText(text: String) {
        if (text.isBlank()) {
            onStateChanged(NanamyState.IDLE)
            return
        }

        Log.d(TAG, "User input from STT: $text")
        onStateChanged(NanamyState.THINKING)

        // 1. Interceptor for Basic Commands (Local & Fast)
        if (interceptBasicCommands(text)) {
            return
        }

        if (!isNetworkAvailable()) {
            if (NanamyVoiceConfig.localLlmEnabled) {
                processLocalLlm(text)
            } else {
                Log.w(TAG, "No network detected and Local LLM is disabled.")
                context.mainExecutor.execute { handleAIResponse("[RESPONSE][LANG:es]Necesito conexión a Internet para responder.[/RESPONSE]") }
            }
            return
        }

        if (NanamyVoiceConfig.aiProvider == "gemini") {
            processGemini(text)
        } else {
            val messages = JSONArray()
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", NanamyVoiceConfig.getFullSystemPrompt(context))
            })
            conversationHistory.forEach { messages.put(it) }
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", text)
            })
            callGroq(messages, text)
        }
    }

    private fun processGemini(text: String) {
        val contents = JSONArray()
        // Convert history to Gemini format
        conversationHistory.forEach { msg ->
            val geminiMsg = JSONObject()
            val role = if (msg.getString("role") == "assistant") "model" else "user"
            geminiMsg.put("role", role)
            geminiMsg.put("parts", JSONArray().apply {
                put(JSONObject().apply { put("text", msg.getString("content")) })
            })
            contents.put(geminiMsg)
        }
        // Current user message
        contents.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().apply {
                put(JSONObject().apply { put("text", text) })
            })
        })
        callGemini(contents, text)
    }

    private fun callGemini(contents: JSONArray, originalUserText: String? = null) {
        val keys = NanamyVoiceConfig.geminiKeys
        if (keys.isEmpty()) {
            Log.e(TAG, "No active Gemini API Keys found")
            context.mainExecutor.execute { handleAIResponse("[RESPONSE][LANG:es]No hay llaves de Gemini activas en los ajustes.[/RESPONSE]") }
            return
        }

        // Start from the last known working key index
        var startIndex = NanamyVoiceConfig.currentGeminiKeyIndex
        if (startIndex >= keys.size || startIndex < 0) {
            startIndex = 0
            NanamyVoiceConfig.currentGeminiKeyIndex = 0
        }

        tryCallWithFallback(contents, originalUserText, keys, startIndex)
    }

    private fun tryCallWithFallback(contents: JSONArray, originalUserText: String?, keys: List<String>, keyIndex: Int) {
        if (keyIndex >= keys.size) {
            Log.e(TAG, "All Gemini keys failed")
            context.mainExecutor.execute { onStateChanged(NanamyState.IDLE) }
            return
        }

        val apiKey = keys[keyIndex]
        val systemInstruction = JSONObject().apply {
            put("parts", JSONArray().apply {
                put(JSONObject().apply { put("text", NanamyVoiceConfig.getFullSystemPrompt(context)) })
            })
        }

        val generationConfig = JSONObject().apply {
            put("thinkingConfig", JSONObject().apply {
                put("includeThoughts", false)
                put("thinkingLevel", "minimal")
            })
        }

        val payload = JSONObject().apply {
            put("contents", contents)
            put("system_instruction", systemInstruction)
            put("tools", GEMINI_TOOLS)
            put("generationConfig", generationConfig)
        }

        // IMPORTANT: DO NOT CHANGE THIS MODEL. gemini-3.5-flash-lite is the correct and working version.
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=$apiKey"

        Log.d(TAG, "DEBUG SIZE - Gemini Contents: ${contents.length()} | Using Key Index: $keyIndex")

        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Gemini API Failure (Key $keyIndex): ${e.message}")
                // For network failure, we also try next key
                tryCallWithFallback(contents, originalUserText, keys, keyIndex + 1)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                
                if (response.code == 429) {
                    Log.w(TAG, "Gemini key $keyIndex failed (429), trying key ${keyIndex + 1}")
                    NanamyVoiceConfig.currentGeminiKeyIndex = keyIndex + 1
                    tryCallWithFallback(contents, originalUserText, keys, keyIndex + 1)
                    return
                }

                if (!response.isSuccessful || body == null) {
                    Log.e(TAG, "Gemini Error (Key $keyIndex): ${response.code} $body")
                    NanamyVoiceConfig.currentGeminiKeyIndex = keyIndex + 1
                    tryCallWithFallback(contents, originalUserText, keys, keyIndex + 1)
                    return
                }

                try {
                    val json = JSONObject(body)
                    val candidates = json.getJSONArray("candidates")
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.getJSONObject("content")
                    val parts = content.getJSONArray("parts")

                    var textResponse: String? = null
                    val originalParts = parts 

                    val hasFunctionCall = (0 until parts.length()).any { parts.getJSONObject(it).has("functionCall") }

                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        if (part.has("text")) {
                            textResponse = part.getString("text")
                        }
                    }

                    if (hasFunctionCall) {
                        handleGeminiToolCalls(contents, originalParts, originalUserText)
                    } else if (textResponse != null) {
                        if (originalUserText != null) {
                            addToHistory(originalUserText, textResponse)
                        }
                        context.mainExecutor.execute { handleAIResponse(textResponse) }
                    } else {
                        context.mainExecutor.execute { onStateChanged(NanamyState.IDLE) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Gemini Parsing error: ${e.message}")
                    // On parsing error we also try next key just in case it's a model-specific response glitch
                    NanamyVoiceConfig.currentGeminiKeyIndex = keyIndex + 1
                    tryCallWithFallback(contents, originalUserText, keys, keyIndex + 1)
                }
            }
        })
    }

    private fun handleGeminiToolCalls(contents: JSONArray, originalParts: JSONArray, originalUserText: String?) {
        // Add model's original message (including functionCalls and thoughtSignatures) to history
        contents.put(JSONObject().apply {
            put("role", "model")
            put("parts", originalParts)
        })

        val toolResponseParts = JSONArray()
        for (i in 0 until originalParts.length()) {
            val part = originalParts.getJSONObject(i)
            if (part.has("functionCall")) {
                val call = part.getJSONObject("functionCall")
                val name = call.getString("name")
                val args = call.optJSONObject("args") ?: JSONObject()

                Log.d(TAG, "Executing Gemini tool: $name with args: $args")

                val result = when (name) {
                    "get_system_info" -> getSystemInfo(args.optString("category", "all"))
                    "uninstall_app" -> uninstallApp(args.optString("app_name", ""))
                    "explore_path" -> explorePath(args.optString("path", ""))
                    "delete_file" -> deleteFile(args.optString("path", ""))
                    "open_app" -> openApp(args.optString("app_name", ""))
                    "get_calendar_events" -> getCalendarEvents(args.optString("date_from", ""), args.optString("date_to", ""))
                    "add_calendar_event" -> addCalendarEvent(args)
                    "add_recurring_event" -> addRecurringEvent(args)
                    "delete_calendar_event" -> deleteCalendarEvent(args.optString("title", ""), args.optString("date_or_day", "any"))
                    "edit_calendar_event" -> editCalendarEvent(args)
                    "get_notes" -> getNotesTool()
                    "add_note" -> addNoteTool(args)
                    "delete_note" -> deleteNoteTool(args)
                    "list_pending_messages" -> listPendingMessagesTool()
                    "read_conversation" -> readConversationTool(args.optString("contact_name_or_key", ""))
                    "reply_to_message" -> replyToMessageTool(args.optString("contact_name_or_key", ""), args.optString("message_text", ""))
                    else -> "Unknown tool"
                }
                
                Log.d(TAG, "Gemini Tool result for $name (length ${result.length}): $result")

                toolResponseParts.put(JSONObject().apply {
                    put("functionResponse", JSONObject().apply {
                        put("name", name)
                        put("response", JSONObject().apply { put("content", result) })
                    })
                })
            }
        }

        contents.put(JSONObject().apply {
            put("role", "user")
            put("parts", toolResponseParts)
        })

        callGemini(contents, originalUserText)
    }

    private fun callGroq(messages: JSONArray, originalUserText: String? = null) {
        val payload = JSONObject().apply {
            put("model", NanamyVoiceConfig.groqModel)
            put("messages", messages)
            put("tools", TOOLS)
            put("tool_choice", "auto")
            put("reasoning_format", "hidden")
            put("reasoning_effort", "none")
            put("max_tokens", 4096)
        }

        val jsonString = payload.toString()
        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${NanamyVoiceConfig.groqApiKey}")
            .post(jsonString.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Groq API Failure: ${e.message}")
                context.mainExecutor.execute { onStateChanged(NanamyState.IDLE) }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    Log.e(TAG, "Groq Error: ${response.code} $body")
                    context.mainExecutor.execute { onStateChanged(NanamyState.IDLE) }
                    return
                }

                try {
                    val json = JSONObject(body)
                    if (json.has("usage")) {
                        val usage = json.getJSONObject("usage")
                        Log.d(TAG, "Usage: prompt_tokens=${usage.optInt("prompt_tokens")} reasoning_tokens=${usage.optInt("reasoning_tokens", -1)}")
                    }
                    val message = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message")

                    if (message.has("tool_calls")) {
                        handleToolCalls(messages, message.getJSONArray("tool_calls"), originalUserText)
                    } else {
                        val content = message.getString("content")
                        if (originalUserText != null) {
                            addToHistory(originalUserText, content)
                        }
                        context.mainExecutor.execute { handleAIResponse(content) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Parsing error: ${e.message}")
                    context.mainExecutor.execute { onStateChanged(NanamyState.IDLE) }
                }
            }
        })
    }

    private fun handleToolCalls(messages: JSONArray, toolCalls: JSONArray, originalUserText: String?) {
        val assistantMessage = JSONObject().apply {
            put("role", "assistant")
            put("tool_calls", toolCalls)
        }
        messages.put(assistantMessage)

        for (i in 0 until toolCalls.length()) {
            val call = toolCalls.getJSONObject(i)
            val id = call.getString("id")
            val function = call.getJSONObject("function")
            val name = function.getString("name")
            val args = JSONObject(function.getString("arguments"))

            val result = when (name) {
                "get_system_info" -> getSystemInfo(args.optString("category", "all"))
                "uninstall_app" -> uninstallApp(args.optString("app_name", ""))
                "explore_path" -> explorePath(args.optString("path", ""))
                "delete_file" -> deleteFile(args.optString("path", ""))
                "open_app" -> openApp(args.optString("app_name", ""))
                "get_calendar_events" -> getCalendarEvents(args.optString("date_from", ""), args.optString("date_to", ""))
                "add_calendar_event" -> addCalendarEvent(args)
                "add_recurring_event" -> addRecurringEvent(args)
                "delete_calendar_event" -> deleteCalendarEvent(args.optString("title", ""), args.optString("date_or_day", "any"))
                "edit_calendar_event" -> editCalendarEvent(args)
                "get_notes" -> getNotesTool()
                "add_note" -> addNoteTool(args)
                "delete_note" -> deleteNoteTool(args)
                "list_pending_messages" -> listPendingMessagesTool()
                "read_conversation" -> readConversationTool(args.optString("contact_name_or_key", ""))
                "reply_to_message" -> replyToMessageTool(args.optString("contact_name_or_key", ""), args.optString("message_text", ""))
                else -> "Unknown tool"
            }

            messages.put(JSONObject().apply {
                put("role", "tool")
                put("tool_call_id", id)
                put("name", name)
                put("content", result)
            })
        }

        callGroq(messages, originalUserText)
    }

    private fun addToHistory(userText: String, assistantText: String) {
        conversationHistory.add(JSONObject().apply {
            put("role", "user")
            put("content", userText)
        })
        conversationHistory.add(JSONObject().apply {
            put("role", "assistant")
            put("content", assistantText)
        })

        while (conversationHistory.size > MAX_HISTORY_TURNS * 2) {
            conversationHistory.removeFirst()
            conversationHistory.removeFirst()
        }
    }

    private fun getCalendarEvents(from: String, to: String): String {
        return try {
            val fromDate = LocalDate.parse(from)
            val toDate = LocalDate.parse(to)
            val entries = calendarParser.getEntriesInRange(fromDate, toDate)
            
            // Filter out past events
            val now = LocalDateTime.now()
            val futureEntries = entries.filter { entry ->
                if (entry.allDay) {
                    // All day events stay visible until the day ends
                    !entry.date.isBefore(now.toLocalDate())
                } else {
                    // Specific time events disappear exactly when their time passes
                    val entryDateTime = entry.date.atTime(entry.time ?: LocalTime.MAX)
                    entryDateTime.isAfter(now)
                }
            }

            if (futureEntries.isEmpty()) "no upcoming events found in this range"
            else futureEntries.joinToString("\n") { entry ->
                val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)
                val timeStr = if (entry.allDay) "All Day" else entry.time?.format(timeFormatter) ?: "All Day"
                "${entry.date} ($timeStr): ${entry.title}"
            }
        } catch (e: Exception) {
            "error parsing dates: ${e.message}"
        }
    }

    private fun addCalendarEvent(args: JSONObject): String {
        return try {
            val title = args.getString("title")
            if (isGenericTitle(title)) {
                return "error: generic title detected ('$title'). You must ask the user for a specific title (e.g., 'Meeting with whom?' or 'Appointment for what?') before scheduling. Titles like 'Cita urgente' are still considered generic."
            }
            val date = LocalDate.parse(args.getString("date"))
            val allday = args.optBoolean("allday", !args.has("time"))
            val time = if (allday || !args.has("time")) null else LocalTime.parse(args.getString("time"))
            
            // Robust host-side defaults: use true/10 if missing OR explicitly null/empty in some JSON variants
            val remind = if (args.isNull("remind")) true else args.optBoolean("remind", true)
            val notifyBefore = if (args.isNull("notify_before")) 10 else args.optInt("notify_before", 10)

            Log.d(TAG, "Tool Call add_calendar_event: title=$title, remind=$remind, notify=$notifyBefore")

            val entry = CalendarEntry(title, date, time, allday, remind, notifyBefore)
            calendarParser.appendEvent(entry)
            calendarScheduler.scheduleAlarm(entry)
            calendarViewModel.notifyCalendarChanged()
            "success: event '$title' scheduled (remind=$remind, notify=$notifyBefore)"
        } catch (e: Exception) {
            "error: ${e.message}"
        }
    }

    private fun addRecurringEvent(args: JSONObject): String {
        return try {
            val title = args.getString("title")
            if (isGenericTitle(title)) {
                return "error: generic title detected ('$title'). You must ask the user for a specific title."
            }
            val allday = args.optBoolean("allday", !args.has("time"))
            val time = if (allday || !args.has("time")) null else LocalTime.parse(args.getString("time"))
            
            // Robust host-side defaults
            val remind = if (args.isNull("remind")) true else args.optBoolean("remind", true)
            val notifyBefore = if (args.isNull("notify_before")) 10 else args.optInt("notify_before", 10)

            Log.d(TAG, "Tool Call add_recurring_event: title=$title, remind=$remind, notify=$notifyBefore")

            val entry = CalendarEntry(
                title, LocalDate.MIN, time, allday, remind, notifyBefore,
                isRecurring = true, recurringDay = args.getString("day"), 
                recurringStart = LocalDate.parse(args.getString("start")), 
                recurringEnd = LocalDate.parse(args.getString("end"))
            )
            calendarParser.appendRecurring(entry)
            calendarScheduler.scheduleAlarm(entry)
            calendarViewModel.notifyCalendarChanged()
            "success: recurring added (remind=$remind, notify=$notifyBefore)"
        } catch (e: Exception) {
            "error: ${e.message}"
        }
    }

    private fun deleteCalendarEvent(title: String, dateOrDay: String): String {
        calendarScheduler.cancelAlarm(title)
        return if (calendarParser.deleteEntry(title, dateOrDay)) {
            calendarViewModel.notifyCalendarChanged()
            "success: event '$title' deleted"
        } else {
            "error: event not found"
        }
    }

    private fun editCalendarEvent(args: JSONObject): String {
        val title = args.getString("title")
        val dateOrDay = args.optString("date_or_day", "any")
        
        val updates = mutableMapOf<String, String>()
        val keys = listOf("new_title", "date", "time", "remind", "notify_before", "allday", "day", "start", "end")
        keys.forEach { key ->
            if (args.has(key)) {
                val value = if (key == "new_title") "title" else key
                updates[value] = args.get(key).toString()
            }
        }

        if (calendarParser.editEntry(title, dateOrDay, updates)) {
            // Simplification: reschedule all upcoming to be safe
            val now = LocalDate.now()
            calendarParser.getEntriesInRange(now, now.plusMonths(1))
                .distinctBy { it.title }
                .forEach { calendarScheduler.scheduleAlarm(it) }
            
            calendarViewModel.notifyCalendarChanged()
            return "success: event '$title' updated"
        }
        return "error: event not found"
    }

    private fun getNotesTool(): String {
        val notes = notesParser.getAllNotes()
        return if (notes.isEmpty()) "no notes found"
        else notes.joinToString("\n---\n") { "Title: ${it.title}\nContent: ${it.content}" }
    }

    private fun addNoteTool(args: JSONObject): String {
        val content = args.getString("content")
        val title = args.optString("title", content.take(20).plus("..."))
        val note = NoteEntry(title = title, content = content)
        notesParser.saveNote(note)
        notesViewModel.notifyNotesChanged()
        return "success: note '$title' saved"
    }

    private fun deleteNoteTool(args: JSONObject): String {
        val title = args.getString("title")
        val note = notesParser.getAllNotes().find { it.title.contains(title, true) }
        return if (note != null) {
            notesParser.deleteNote(note.id)
            notesViewModel.notifyNotesChanged()
            "success: note deleted"
        } else "error: note not found"
    }

    private fun isGenericTitle(title: String): Boolean {
        val genericNouns = listOf(
            "cita", "reunión", "reunion", "evento", "recordatorio", "alarma", "algo", "cosa",
            "appointment", "meeting", "event", "reminder", "alarm", "something", "task"
        )
        val emptyAdjectives = listOf(
            "urgente", "importante", "rápida", "rapida", "nueva", "nuevo", "pendiente",
            "urgent", "important", "quick", "new", "pending"
        )
        
        val lowerTitle = title.lowercase().trim()
        
        // 1. Exact match against generic nouns
        if (genericNouns.any { it == lowerTitle }) return true
        
        // 2. Combination of generic noun + empty adjective (any order)
        genericNouns.forEach { noun ->
            emptyAdjectives.forEach { adj ->
                if (lowerTitle == "$noun $adj" || lowerTitle == "$adj $noun" || 
                    lowerTitle.contains("$noun $adj") || lowerTitle.contains("$adj $noun")) return true
            }
        }
        
        // 3. Heuristic: Title contains a generic noun and has very little additional info
        val words = lowerTitle.split(Regex("\\s+")).filter { it.length > 2 }
        if (words.size <= 3 && genericNouns.any { lowerTitle.contains(it) }) {
            val meaningfulWords = words.filter { word ->
                !genericNouns.contains(word) && !emptyAdjectives.contains(word)
            }
            if (meaningfulWords.isEmpty()) return true
        }
        
        return false
    }

    private fun getSystemInfo(category: String): String {
        return when (category) {
            "storage" -> getStorageInfo()
            "battery" -> getBatteryInfo()
            "ram" -> getRamInfo()
            "network" -> getNetworkInfo()
            "datetime" -> getDatetimeInfo()
            "all" -> "${getDatetimeInfo()}\n${getBatteryInfo()}\n${getNetworkInfo()}\n${getStorageInfo()}\n${getRamInfo()}"
            else -> "Unknown category"
        }
    }

    private fun getStorageInfo(): String {
        val stat = StatFs(Environment.getDataDirectory().path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong

        val total = (totalBlocks * blockSize) / (1024 * 1024 * 1024)
        val available = (availableBlocks * blockSize) / (1024 * 1024 * 1024)
        val used = total - available

        return "Storage: Total ${total}GB, Used ${used}GB, Free ${available}GB"
    }

    private fun getBatteryInfo(): String {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, filter)

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = level * 100 / scale.toFloat()

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val temp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempCelsius = temp / 10.0

        return "Battery: ${pct.toInt()}%, Charging: $isCharging, Temp: ${tempCelsius}°C"
    }

    private fun getRamInfo(): String {
        val mi = ActivityManager.MemoryInfo()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.getMemoryInfo(mi)

        val available = mi.availMem / (1024 * 1024)
        val total = mi.totalMem / (1024 * 1024)
        return "RAM: Total ${total}MB, Available ${available}MB"
    }

    private fun getNetworkInfo(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)

        return if (caps != null) {
            val type = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                else -> "Other"
            }
            "Network: Connected via $type"
        } else {
            "Network: Disconnected"
        }
    }

    private fun uninstallApp(appName: String): String {
        if (appName.isBlank()) return "error: missing app name"

        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val matches = installedApps.filter {
            (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
            pm.getApplicationLabel(it).toString().contains(appName, ignoreCase = true)
        }

        return when {
            matches.isEmpty() -> "not_found: no user app matches '$appName'"
            matches.size > 1 -> "ambiguous: found multiple apps: ${matches.joinToString { pm.getApplicationLabel(it) }}"
            else -> {
                val pkg = matches[0].packageName
                val intent = Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.parse("package:$pkg")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                "success: showed native confirmation dialog for $pkg"
            }
        }
    }

    private fun openApp(appName: String): String {
        if (appName.isBlank()) return "error: missing app name"

        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val matches = resolveInfos.filter {
            it.loadLabel(pm).toString().contains(appName, ignoreCase = true)
        }

        return when {
            matches.isEmpty() -> "not_found"
            matches.size > 1 -> "ambiguous: ${matches.joinToString { it.loadLabel(pm).toString() }}"
            else -> {
                val pkg = matches[0].activityInfo.packageName
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                    "app_opened: ${matches[0].loadLabel(pm)}"
                } else {
                    "error: cannot launch app"
                }
            }
        }
    }

    private fun explorePath(path: String): String {
        if (explorePathCallCount >= 3) return "error: budget exceeded (max 3 calls)"
        explorePathCallCount++

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return "error: missing MANAGE_EXTERNAL_STORAGE permission. User redirected to settings."
        }

        val root = Environment.getExternalStorageDirectory()
        val target = when (path.lowercase()) {
            "home", "root", "/" -> root
            "pictures", "fotos", "imágenes" -> File(root, "DCIM")
            "downloads", "descargas" -> File(root, "Download")
            "music", "música" -> File(root, "Music")
            "docs", "documents", "documentos" -> File(root, "Documents")
            else -> if (path.startsWith("/")) File(path) else File(root, path)
        }

        if (!target.exists() || !target.isDirectory) return "error: path not found or is not a folder: ${target.absolutePath}"

        val files = target.listFiles()
        if (files == null) return "error: cannot list files (permission denied)"

        return if (files.isEmpty()) "empty folder"
        else files.take(50).joinToString("\n") {
            val type = if (it.isDirectory) "[DIR]" else "[FILE]"
            "$type ${it.absolutePath}"
        }
    }

    private fun deleteFile(path: String): String {
        val file = File(path)
        if (!file.exists()) return "error: file not found: $path"
        return if (file.delete()) "success: deleted $path" else "error: could not delete $path"
    }

    private fun getDatetimeInfo(): String {
        val now = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy, HH:mm", Locale.ENGLISH)
        return "Current Datetime: ${now.format(formatter)}"
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val actPw = connectivityManager.getNetworkCapabilities(network) ?: return false
        return actPw.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun interceptBasicCommands(text: String): Boolean {
        val lowerText = text.lowercase(Locale.ROOT)
        val isEn = detectedInputLanguage == "en"

        // 1. Battery Check
        if (lowerText.contains(Regex("\\b(batería|carga|pila|battery|charge)\\b"))) {
            val level = getBatteryLevelInt()
            val responses = if (isEn) {
                listOf(
                    "Battery is at $level%.",
                    "You have $level% remaining.",
                    "Current charge is $level%.",
                    "It's at $level%.",
                    "$level% battery left."
                )
            } else {
                listOf(
                    "Tienes un $level% de batería.",
                    "Te queda un $level% de energía.",
                    "La batería está al $level%.",
                    "Aún tienes un $level%, no te preocupes.",
                    "Carga actual: $level%."
                )
            }
            currentTtsLanguage = if (isEn) "en" else "es"
            speak(responses.random())
            return true
        }

        // 2. Time Check
        if (lowerText.contains(Regex("\\b(hora|time)\\b"))) {
            val time = LocalTime.now().format(DateTimeFormatter.ofPattern(if (isEn) "h:mm a" else "HH:mm"))
            val responses = if (isEn) {
                listOf("It is $time.", "The time is $time.", "Currently $time.")
            } else {
                listOf("Son las $time.", "La hora actual es $time.", "Actualmente son las $time.")
            }
            currentTtsLanguage = if (isEn) "en" else "es"
            speak(responses.random())
            return true
        }

        // 3. Identity Check (Avoid LLM identity hallucinations)
        if (lowerText.contains(Regex("\\b(quién eres|como te llamas|quien eres|tu nombre|who are you|your name)\\b"))) {
            val responses = if (isEn) {
                listOf("I am Nanamy, your personal assistant.", "My name is Nanamy.", "I'm Nanamy, here to help.")
            } else {
                listOf("Soy Nanamy, tu asistente personal.", "Me llamo Nanamy.", "Soy Nanamy, ¿en qué puedo ayudarte?")
            }
            currentTtsLanguage = if (isEn) "en" else "es"
            speak(responses.random())
            return true
        }

        return false
    }

    private fun getBatteryLevelInt(): Int {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, filter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level == -1 || scale == -1) 0 else (level * 100 / scale.toFloat()).toInt()
    }

    private fun processLocalLlm(text: String) {
        scope.launch {
            val engine = LocalLlmEngine.getInstance()
            if (!engine.isLoaded()) {
                val success = engine.loadModel(context)
                if (!success) {
                    currentTtsLanguage = "es"
                    speak("No pude cargar el modelo local. Revisa los ajustes.")
                    return@launch
                }
            }

            currentTtsLanguage = detectedInputLanguage
            
            val systemPrompt = NanamyVoiceConfig.getLocalLlmFullSystemPrompt().trim()
            val modelPath = NanamyVoiceConfig.localLlmModelPath.lowercase()
            
            val isQwen = modelPath.contains("qwen")
            val isLlama = modelPath.contains("llama") || (!isQwen) // Llama format as safer fallback
            
            val historyBuilder = StringBuilder()
            val localPrompt: String

            if (isQwen) {
                // Qwen / ChatML format
                conversationHistory.forEach { msg ->
                    val role = msg.getString("role")
                    val content = msg.getString("content").trim()
                    historyBuilder.append("<|im_start|>$role\n$content<|im_end|>\n")
                }
                localPrompt = "<|im_start|>system\n$systemPrompt<|im_end|>\n" +
                              historyBuilder.toString() +
                              "<|im_start|>user\n${text.trim()}<|im_end|>\n" +
                              "<|im_start|>assistant\n"
            } else {
                // Llama 3.x format
                conversationHistory.forEach { msg ->
                    val role = msg.getString("role")
                    val content = msg.getString("content").trim()
                    historyBuilder.append("<|start_header_id|>$role<|end_header_id|>\n\n$content<|eot_id|>")
                }
                localPrompt = "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n$systemPrompt<|eot_id|>" +
                              historyBuilder.toString() +
                              "<|start_header_id|>user<|end_header_id|>\n\n${text.trim()}<|eot_id|>" +
                              "<|start_header_id|>assistant<|end_header_id|>\n\n"
            }
            
            val response = engine.generate(localPrompt)
            
            Log.d(TAG, "Local LLM response: $response")
            val cleanedResponse = cleanLocalLlmResponse(response)
            Log.d(TAG, "Cleaned Local LLM response: $cleanedResponse")
            
            // Add this turn to history
            addToHistory(text, cleanedResponse)
            
            context.mainExecutor.execute { speak(cleanedResponse) }
        }
    }

    private fun cleanLocalLlmResponse(response: String): String {
        var text = response.trim()

        // 1. Cut at common dialogue markers and technical tokens
        val stopTokens = listOf(
            "User:", "Assistant:", "System:", "Usuario:", "Asistente:", "Sistema:", 
            "###", "---", "<|im_end|>", "<|im_start|>", "<|endoftext|>", "<|prompt|>", "<|answer|>"
        )
        for (token in stopTokens) {
            val index = text.indexOf(token, ignoreCase = true)
            if (index != -1) {
                text = text.substring(0, index).trim()
            }
        }

        // 2. Remove system instructions leakage
        val sysPrompt = settingsRepository.localLlmSystemPrompt.trim()
        if (sysPrompt.isNotEmpty()) {
            text = text.replace(sysPrompt, "", ignoreCase = true).trim()
        }
        val persona = settingsRepository.localLlmUserPersonaPrompt.trim()
        if (persona.isNotEmpty()) {
            text = text.replace(persona, "", ignoreCase = true).trim()
        }

        // 3. Detect and cut loops / repetitions
        // Split by lines and check for duplicates
        val lines = text.split("\n")
        val seenLines = mutableSetOf<String>()
        val filteredLines = mutableListOf<String>()
        for (line in lines) {
            val cleanLine = line.trim().lowercase()
            if (cleanLine.isEmpty()) continue
            
            // If we see a repeating line of significant length, it's likely a loop
            // We cut the response here to stop the repetition
            if (seenLines.contains(cleanLine) && cleanLine.length > 3) {
                break
            }
            seenLines.add(cleanLine)
            filteredLines.add(line)
        }

        val result = filteredLines.joinToString(" ").trim()

        // 4. Intra-line repetition check (e.g. "Hello hello hello")
        // We look for repeated sequences of words
        val words = result.split(Regex("\\s+"))
        if (words.size > 4) {
            // Check for repeating patterns of 1 to 5 words
            for (patternLen in 1..5) {
                if (words.size < patternLen * 2) continue
                for (i in 0 until words.size - patternLen * 2 + 1) {
                    val first = words.subList(i, i + patternLen)
                    val second = words.subList(i + patternLen, i + patternLen * 2)
                    if (first == second && first.joinToString("").length > 3) {
                        // Loop found, cut everything after the first occurrence
                        return words.subList(0, i + patternLen).joinToString(" ").trim()
                    }
                }
            }
        }

        return result
    }


    private fun handleAIResponse(content: String) {
        Log.d(TAG, "Raw content from AI: $content")

        val speechCandidate = extractResponse(content)

        val pattern = Pattern.compile("^\\[LANG:(es|en)]", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(speechCandidate)

        var language = "es"
        var cleanText = speechCandidate

        if (matcher.find()) {
            language = matcher.group(1)?.lowercase(Locale.ROOT) ?: "es"
            cleanText = speechCandidate.substring(matcher.end()).trim()
        }

        Log.d(TAG, "Parsed language: $language, Clean text: $cleanText")
        currentTtsLanguage = language
        speak(cleanText)
    }

    private fun extractResponse(text: String): String {
        val responsePattern = Pattern.compile("\\[RESPONSE](.*?)\\[/RESPONSE]", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        val matcher = responsePattern.matcher(text)

        var result = if (matcher.find()) {
            matcher.group(1)?.trim() ?: text
        } else {
            text
        }

        result = result.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
        result = result.replace(Regex("<think>.*", RegexOption.DOT_MATCHES_ALL), "")
        return result.trim()
    }

    private fun speak(text: String) {
        if (text.isBlank()) {
            onStateChanged(NanamyState.IDLE)
            return
        }

        val locale = if (currentTtsLanguage == "en") Locale.US else Locale("es", "ES")

        tts?.language = locale
        tts?.setSpeechRate(settingsRepository.ttsSpeechRate)
        tts?.setPitch(settingsRepository.ttsPitch)

        tts?.voices?.let { voices ->
            val targetVoiceName = if (currentTtsLanguage == "en") "en-us-x-sfg-local" else "es-es-x-eea-local"
            val exactVoice = voices.find { it.name == targetVoiceName }
            if (exactVoice != null) {
                tts?.voice = exactVoice
            }
        }

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nanamy_utterance")
    }

    fun onAssistantFocused() {
        // Reverted: Automatic preload disabled to avoid issues if speaking during load
        Log.d(TAG, "Assistant focused - (Manual load only)")
    }

    fun onAssistantBlurred() {
        Log.d(TAG, "Releasing local model because assistant is blurred/paused")
        preloadJob?.cancel()
        preloadJob = null
        LocalLlmEngine.getInstance().unloadModel()
    }

    fun destroy() {
        speechRecognizer.destroy()
        tts?.stop()
        tts?.shutdown()
        // No descargamos el modelo aquí para mantenerlo en caché para la próxima vez
    }

    // RecognitionListener Overrides
    override fun onReadyForSpeech(params: Bundle?) {
        Log.d(TAG, "onReadyForSpeech | Mic ready")
    }
    override fun onBeginningOfSpeech() {
        Log.d(TAG, "onBeginningOfSpeech | User started talking")
    }
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {
        Log.d(TAG, "onEndOfSpeech | User stopped talking")
        isListening = false
    }

    override fun onError(error: Int) {
        val errorMsgStr = when(error) {
            1 -> "NETWORK_TIMEOUT"
            2 -> "NETWORK"
            3 -> "AUDIO"
            4 -> "SERVER"
            5 -> "CLIENT"
            6 -> "SPEECH_TIMEOUT"
            7 -> "NO_MATCH"
            8 -> "RECOGNIZER_BUSY"
            9 -> "INSUFFICIENT_PERMISSIONS"
            else -> "UNKNOWN ($error)"
        }
        
        Log.e(TAG, "onError | Error: $errorMsgStr")
        isListening = false
        onStateChanged(NanamyState.IDLE)
        
        val errorMsg = when(error) {
            1 -> "Error de red (Timeout)"
            2 -> "Sin red. Necesitas internet o paquetes de voz offline."
            3 -> "Error de audio"
            4 -> "Error del servidor"
            5 -> "Error del cliente"
            6 -> "Tiempo de escucha agotado"
            7 -> "No se entendió nada"
            8 -> "Reconocedor ocupado"
            9 -> "Sin permisos de micrófono"
            13 -> "Idioma no soportado para modo offline (Error 13)"
            else -> "Error desconocido ($error)"
        }
        
        context.mainExecutor.execute {
            speak("[RESPONSE][LANG:es]$errorMsg[/RESPONSE]")
        }
    }

    private fun isHotwordMatch(heardText: String, hotword: String): Boolean {
        // Normalización fonética básica para español (i/y, b/v, h, acentos)
        fun normalize(text: String): String {
            return text.lowercase()
                .replace("y", "i")
                .replace("v", "b")
                .replace("h", "")
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .replace(" ", "")
                .trim()
        }

        val normHeard = normalize(heardText)
        val normHot = normalize(hotword)

        // Verificamos si la palabra normalizada está presente
        return normHeard.contains(normHot)
    }

    private fun extractCommand(heardText: String, hotword: String): String {
        // Intentamos encontrar dónde termina la palabra clave para extraer el resto
        // Usamos una versión con espacios para no romper el comando
        val lowerHeard = heardText.lowercase()
        val lowerHot = hotword.lowercase()
        
        // Caso 1: Match exacto (con y)
        if (lowerHeard.contains(lowerHot)) {
            return heardText.substring(lowerHeard.indexOf(lowerHot) + lowerHot.length).trim()
        }
        
        // Caso 2: Match fonético (nanami)
        val phoneticHot = lowerHot.replace("y", "i")
        if (lowerHeard.contains(phoneticHot)) {
            return heardText.substring(lowerHeard.indexOf(phoneticHot) + phoneticHot.length).trim()
        }

        return ""
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        Log.d(TAG, "onResults | matches: ${matches.take(3)}")
        
        if (matches.isEmpty()) {
            handleEmptyResults()
            return
        }

        // Simplest flow: Process the best match and go back to IDLE
        isListening = false
        isWaitingForCommand = false
        processHeardText(matches[0])
    }

    private fun processHeardText(text: String) {
        detectedInputLanguage = "es"
        if (text.contains(Regex("\\b(the|and|you|is|it|to|of)\\b", RegexOption.IGNORE_CASE))) {
            detectedInputLanguage = "en"
        }
        processUserText(text)
    }

    private fun handleEmptyResults() {
        Log.d(TAG, "onResults | Empty results")
        isListening = false
        onStateChanged(NanamyState.IDLE)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        // No logic needed here for Stage 2
    }
    override fun onEvent(eventType: Int, params: Bundle?) {}

    // TextToSpeech.OnInitListener
    override fun onInit(status: Int) {
        Log.d(TAG, "TTS onInit status: $status")
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("es", "ES")
        } else {
            Log.e(TAG, "TTS Initialization failed with status: $status")
        }
    }

    private fun listPendingMessagesTool(): String {
        return try {
            val db = NanamyDatabase.getInstance(context)
            val conversations = runBlocking { db.messageDao().getLatestConversations() }
            if (conversations.isEmpty()) {
                "No pending or recent messages found."
            } else {
                val jsonArray = JSONArray()
                for (conv in conversations) {
                    jsonArray.put(JSONObject().apply {
                        put("convKey", conv.convKey)
                        put("packageName", conv.packageName)
                        put("senderName", conv.senderName)
                        put("lastMessage", conv.messageText)
                        put("timestamp", conv.timestamp)
                        put("isOutgoing", conv.isOutgoing)
                    })
                }
                jsonArray.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in listPendingMessagesTool", e)
            "Error querying messages: ${e.message}"
        }
    }

    private fun readConversationTool(contactQuery: String): String {
        return try {
            val db = NanamyDatabase.getInstance(context)
            val latest = runBlocking { db.messageDao().getLatestConversations() }
            val match = findBestConversationMatch(latest, contactQuery)
            if (match.ambiguousCandidates.isNotEmpty()) {
                return "AMBIGUOUS: Multiple contacts match '$contactQuery': ${match.ambiguousCandidates.joinToString(", ")}. Please specify which contact you mean."
            }
            val targetConvKey = match.bestConvKey ?: return "No conversation found for contact '$contactQuery'."

            val messages = runBlocking { db.messageDao().getMessagesForConversation(targetConvKey) }
            if (messages.isEmpty()) {
                "No message history found for $contactQuery."
            } else {
                val jsonArray = JSONArray()
                for (msg in messages) {
                    jsonArray.put(JSONObject().apply {
                        put("sender", if (msg.isOutgoing) "Me" else msg.senderName)
                        put("text", msg.messageText)
                        put("timestamp", msg.timestamp)
                    })
                }
                jsonArray.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in readConversationTool", e)
            "Error reading conversation: ${e.message}"
        }
    }

    private fun replyToMessageTool(contactQuery: String, messageText: String): String {
        return try {
            val debugTag = "NanamyDebug"
            Log.d(debugTag, "replyToMessageTool | LLM input contactQuery='$contactQuery', messageText='$messageText'")

            val db = NanamyDatabase.getInstance(context)
            val latest = runBlocking { db.messageDao().getLatestConversations() }
            val match = findBestConversationMatch(latest, contactQuery)

            if (match.ambiguousCandidates.isNotEmpty()) {
                Log.w(debugTag, "replyToMessageTool | AMBIGUOUS match for '$contactQuery': ${match.ambiguousCandidates}")
                return "AMBIGUOUS: Multiple contacts match '$contactQuery': ${match.ambiguousCandidates.joinToString(", ")}. Please ask the user to clarify which contact they mean."
            }

            val targetConvKey = match.bestConvKey
            Log.d(debugTag, "replyToMessageTool | Fuzzy match resolved '$contactQuery' -> targetConvKey='$targetConvKey'")

            if (targetConvKey == null) {
                Log.w(debugTag, "replyToMessageTool | No active conversation found in DB for '$contactQuery'")
                return "No active conversation found for contact '$contactQuery'."
            }

            val history = runBlocking { db.messageDao().getMessagesForConversation(targetConvKey) }
            val lastMsgText = history.lastOrNull { !it.isOutgoing }?.messageText ?: history.lastOrNull()?.messageText ?: ""
            val isSpanish = lastMsgText.contains(Regex("[áéíóúñ¿¡a-z]", RegexOption.IGNORE_CASE))

            val finalReplyText = if (messageText.isNotBlank()) {
                messageText
            } else {
                if (isSpanish) "Sí, te respondo en un rato" else "im a bit busy right now, ill text you later"
            }

            Log.d(debugTag, "replyToMessageTool | Attempting reply to targetConvKey='$targetConvKey' with finalReplyText='$finalReplyText'")
            val replier = NotificationReplier(context, db)
            val result = runBlocking { replier.reply(targetConvKey, finalReplyText) }

            when (result) {
                is ReplyResult.Sent -> {
                    Log.d(debugTag, "replyToMessageTool | SUCCESS sending reply to '$targetConvKey'")
                    "SUCCESS: Message sent to $contactQuery: '$finalReplyText'"
                }
                is ReplyResult.Failed -> {
                    Log.w(debugTag, "replyToMessageTool | FAILED sending reply to '$targetConvKey': ${result.reason}")
                    "FAILED: Could not send reply automatically (${result.reason}). Fallback option offered to open app."
                }
            }
        } catch (e: Exception) {
            Log.e("NanamyDebug", "replyToMessageTool | Exception: ${e.message}", e)
            "FAILED: Exception while executing reply: ${e.message}"
        }
    }

    private data class MatchResult(
        val bestConvKey: String?,
        val ambiguousCandidates: List<String> = emptyList()
    )

    private fun findBestConversationMatch(conversations: List<MessageEntity>, query: String): MatchResult {
        if (conversations.isEmpty() || query.isBlank()) return MatchResult(null)

        val normQuery = com.nanamy.launcher.messages.ActionStore.normalizeKey(query.lowercase())

        conversations.find { com.nanamy.launcher.messages.ActionStore.normalizeKey(it.convKey.lowercase()) == normQuery }?.let {
            return MatchResult(it.convKey)
        }

        val candidates = conversations.filter {
            val name = com.nanamy.launcher.messages.ActionStore.normalizeKey(it.senderName.lowercase())
            name.contains(normQuery) || normQuery.contains(name)
        }

        return when {
            candidates.size == 1 -> MatchResult(candidates[0].convKey)
            candidates.size > 1 -> {
                val names = candidates.map { it.senderName }.distinct()
                if (names.size == 1) MatchResult(candidates[0].convKey)
                else MatchResult(null, names)
            }
            else -> MatchResult(null)
        }
    }
}
