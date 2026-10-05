package com.nanamy.launcher.localllm

import android.content.Context
import android.util.Log
import com.nanamy.launcher.voice.NanamyVoiceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class LocalLlmEngine private constructor() {

    companion object {
        private const val TAG = "LocalLlmEngine"
        
        @Volatile
        private var instance: LocalLlmEngine? = null

        fun getInstance(): LocalLlmEngine {
            return instance ?: synchronized(this) {
                instance ?: LocalLlmEngine().also { instance = it }
            }
        }

        init {
            try {
                // Load dependencies first if they are shared libraries
                // Note: The order matters if there are internal dependencies
                System.loadLibrary("ggml")
                System.loadLibrary("llama")
                System.loadLibrary("llama-common")
                System.loadLibrary("nanamy-llama")
                Log.d(TAG, "Native library loaded successfully")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to load native library: ${e.message}", e)
            }
        }
    }

    private var isModelLoaded = false
    private var isUnloadRequested = false
    private val loadMutex = Mutex()

    suspend fun loadModel(context: Context): Boolean = loadMutex.withLock {
        if (isModelLoaded) return@withLock true
        isUnloadRequested = false
        
        withContext(Dispatchers.IO) {
            val modelPath = NanamyVoiceConfig.localLlmModelPath
            if (modelPath.isEmpty()) {
                Log.e(TAG, "No model path configured in settings")
                return@withContext false
            }

            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                Log.e(TAG, "Model file not found at: $modelPath")
                return@withContext false
            }
            
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            val success = initModelNative(modelFile.absolutePath, nativeLibDir)
            
            if (isUnloadRequested) {
                if (success) unloadModelNative()
                isModelLoaded = false
                Log.d(TAG, "Model load cancelled by immediate unload")
                return@withContext false
            }

            isModelLoaded = success
            Log.d(TAG, "Model load status: $isModelLoaded")
            isModelLoaded
        }
    }

    suspend fun generate(prompt: String): String = withContext(Dispatchers.Default) {
        if (!isModelLoaded) return@withContext "Error: Model not loaded"
        generateNative(prompt)
    }

    fun unloadModel() {
        isUnloadRequested = true
        if (isModelLoaded) {
            unloadModelNative()
            isModelLoaded = false
            Log.d(TAG, "Model unloaded")
        }
    }

    fun isLoaded(): Boolean = isModelLoaded

    private external fun initModelNative(path: String, nativeLibDir: String): Boolean
    private external fun unloadModelNative()
    private external fun generateNative(prompt: String): String
}
