package com.nanamy.launcher

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import com.nanamy.launcher.voice.VoiceAssistantManager
import kotlin.math.roundToInt

/**
 * Manages the "Rest Mode" (Sleep Mode) features:
 * - Always On Display (Screen Stay Awake).
 * - Automatic volume management.
 */
class RestModeManager(private val activity: Activity) {

    private val vibrator = activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private val audioManager = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    private val settingsRepository by lazy {
        (activity.application as NanamyApplication).settingsRepository
    }

    private var _isRestModeActive = false
    val isRestModeActive: Boolean get() = _isRestModeActive

    private var voiceAssistantManager: VoiceAssistantManager? = null
    private var originalVolume: Int = -1

    fun setVoiceAssistantManager(manager: VoiceAssistantManager) {
        this.voiceAssistantManager = manager
    }

    fun toggleRestMode() {
        if (_isRestModeActive) {
            stopRestMode()
        } else {
            startRestMode()
        }
    }

    fun startRestMode() {
        if (_isRestModeActive) return
        _isRestModeActive = true
        android.util.Log.d("RestMode", "Starting Rest Mode...")

        // Keep screen on
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Set Rest Mode Volume
        try {
            originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetPercent = settingsRepository.restModeVolume
            val targetVol = (maxVol * (targetPercent / 100.0)).roundToInt()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
        } catch (e: Exception) {
            android.util.Log.e("RestMode", "Failed to set volume", e)
        }

        // Note: Wake Word is handled globally by MainActivity based on isRestModeActive setting
        
        vibrate(200)
        android.util.Log.d("RestMode", "Rest Mode started successfully")
        (activity as? MainActivity)?.onRestModeChanged()
    }

    fun stopRestMode() {
        if (!_isRestModeActive) return
        _isRestModeActive = false

        // Reset screen stay awake
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Restore Volume
        if (originalVolume != -1) {
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
            } catch (e: Exception) {
                android.util.Log.e("RestMode", "Failed to restore volume", e)
            }
            originalVolume = -1
        }
        
        vibrate(100)
        android.util.Log.d("RestMode", "Rest Mode stopped")
        (activity as? MainActivity)?.onRestModeChanged()
    }

    private fun vibrate(ms: Long) {
        vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
