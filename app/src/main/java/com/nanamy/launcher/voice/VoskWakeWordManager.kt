package com.nanamy.launcher.voice

import android.content.Context
import android.util.Log
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

/**
 * Stage 1: Passive Wake Word detection using Vosk (Offline).
 */
class VoskWakeWordManager(
    private val context: Context,
    private val onDetected: () -> Unit
) : org.vosk.android.RecognitionListener {

    private var speechService: SpeechService? = null
    private var isInitializing = false

    fun start() {
        if (speechService != null || isInitializing) return
        isInitializing = true
        
        Log.d("VoskWakeWord", "Model loading started...")
        
        // Debug: List what's in assets to verify path
        try {
            val list = context.assets.list("vosk-model")
            Log.d("VoskWakeWord", "Assets in 'vosk-model': ${list?.joinToString()}")
        } catch (e: Exception) {
            Log.e("VoskWakeWord", "Failed to list assets/vosk-model", e)
        }

        StorageService.unpack(context, "vosk-model", "vosk",
            { model: Model ->
                Log.d("VoskWakeWord", "Model ready!")
                setupRecognizer(model)
            },
            { exception: IOException ->
                Log.e("VoskWakeWord", "UNPACK ERROR: ${exception.message}", exception)
                isInitializing = false
            }
        )
    }

    private fun setupRecognizer(model: Model) {
        try {
            // High-efficiency grammar for wake-word only
            val recognizer = Recognizer(model, 16000.0f, "[\"nanamy\", \"nanami\", \"nana mi\", \"[unk]\"]")
            
            speechService = SpeechService(recognizer, 16000.0f)
            val started = speechService?.startListening(this)
            
            Log.d("VoskWakeWord", "Vosk listening started: $started")
            isInitializing = false
        } catch (e: Exception) {
            Log.e("VoskWakeWord", "Error in setupRecognizer", e)
            isInitializing = false
        }
    }

    fun stop() {
        speechService?.let {
            it.stop()
            it.shutdown()
            speechService = null
            Log.d("VoskWakeWord", "Vosk stopped")
        }
        isInitializing = false
    }

    fun isRunning(): Boolean = speechService != null

    override fun onPartialResult(hypothesis: String?) {
        checkHypothesis(hypothesis)
    }

    override fun onResult(hypothesis: String?) {
        Log.d("VoskWakeWord", "onResult: $hypothesis")
        checkHypothesis(hypothesis)
    }

    override fun onFinalResult(hypothesis: String?) {
        checkHypothesis(hypothesis)
    }

    override fun onError(exception: Exception?) {
        Log.e("VoskWakeWord", "Vosk Error", exception)
        stop()
    }

    override fun onTimeout() {
        Log.d("VoskWakeWord", "Vosk Timeout")
    }

    private fun checkHypothesis(hypothesis: String?) {
        if (hypothesis == null) return
        
        // Hypothesis is JSON, e.g. {"text": "nanamy"}
        if (hypothesis.contains("\"nanamy\"", ignoreCase = true) || 
            hypothesis.contains("\"nanami\"", ignoreCase = true) || 
            hypothesis.contains("\"nana mi\"", ignoreCase = true) ||
            hypothesis.contains("\"nana me\"", ignoreCase = true) ||
            hypothesis.contains("\"nanamé\"", ignoreCase = true)) {
            
            Log.d("VoskWakeWord", "MATCH DETECTED! (Hypothesis: $hypothesis)")
            stop()
            onDetected()
        }
    }
}
