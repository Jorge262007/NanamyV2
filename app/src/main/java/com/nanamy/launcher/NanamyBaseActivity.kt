package com.nanamy.launcher

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity

/**
 * Base activity to handle global visual rotation and robust key detection.
 * Touch transformation is handled by NanamyRotationLayout to avoid double-rotation.
 */
abstract class NanamyBaseActivity : AppCompatActivity() {

    abstract val rootRotationView: View?

    private val keyHandler = Handler(Looper.getMainLooper())
    private var activeKeyCode = -1
    private var isRotationExecuted = false
    private var isRestModeExecuted = false

    private val ROTATION_MS = 1000L
    private val REST_MODE_MS = 2000L
    private val SIGNAL_GRACE_MS = 1000L

    private val keyTimerRunnable = object : Runnable {
        var startTime = 0L
        override fun run() {
            if (activeKeyCode == -1) return
            val elapsed = System.currentTimeMillis() - startTime
            
            if (!isRotationExecuted && elapsed >= ROTATION_MS) {
                if (activeKeyCode == 104 || activeKeyCode == 105) {
                    RotationState.updateIndex(if (activeKeyCode == 104) -1 else 1)
                    isRotationExecuted = true
                    refreshVisualRotation()
                    vibrateBase(80)
                }
            }
            
            if (!isRestModeExecuted && elapsed >= REST_MODE_MS) {
                if (activeKeyCode == 104 || activeKeyCode == 105) {
                    executeRestModeToggle()
                    isRestModeExecuted = true
                    vibrateBase(150)
                }
            }
            
            if (!isRestModeExecuted) {
                keyHandler.postDelayed(this, 50)
            }
        }
    }

    private val cancelKeyRunnable = Runnable {
        keyHandler.removeCallbacks(keyTimerRunnable)
        activeKeyCode = -1
        isRotationExecuted = false
        isRestModeExecuted = false
    }

    override fun onResume() {
        super.onResume()
        refreshVisualRotation()
    }

    fun refreshVisualRotation() {
        rootRotationView?.invalidate()
        rootRotationView?.requestLayout()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == 104 || keyCode == 105 || keyCode == KeyEvent.KEYCODE_BUTTON_L1) {
            keyHandler.removeCallbacks(cancelKeyRunnable)
            if (activeKeyCode != keyCode) {
                activeKeyCode = keyCode
                isRotationExecuted = false
                isRestModeExecuted = false
                keyTimerRunnable.startTime = System.currentTimeMillis()
                keyHandler.removeCallbacks(keyTimerRunnable)
                keyHandler.post(keyTimerRunnable)
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == 104 || keyCode == 105 || keyCode == KeyEvent.KEYCODE_BUTTON_L1) {
            if (keyCode == activeKeyCode) {
                keyHandler.postDelayed(cancelKeyRunnable, SIGNAL_GRACE_MS)
            }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun executeRestModeToggle() {
        (this as? MainActivity)?.restModeManager?.toggleRestMode()
    }

    fun vibrateBase(ms: Long) {
        try {
            val vibrator = getSystemService(Vibrator::class.java)
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }
}
