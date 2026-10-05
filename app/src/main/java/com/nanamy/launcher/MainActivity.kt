package com.nanamy.launcher

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import android.view.View
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nanamy.launcher.eyes.NanamyState
import com.nanamy.launcher.databinding.ActivityMainBinding
import com.nanamy.launcher.voice.VoiceTriggerViewModel
import com.nanamy.launcher.voice.VoskWakeWordManager
import kotlinx.coroutines.launch

/**
 * Main Activity hosting the ViewPager2.
 */
class MainActivity : NanamyBaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private val voiceTriggerViewModel: VoiceTriggerViewModel by viewModels()
    val repo by lazy { (application as NanamyApplication).settingsRepository }

    lateinit var restModeManager: RestModeManager
    private lateinit var voskManager: VoskWakeWordManager

    override val rootRotationView: View get() = binding.mainRoot

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d("MainActivity", "Screen OFF -> Stopping Vosk wake word")
                    voskManager.stop()
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.d("MainActivity", "Screen ON -> Re-evaluating wake word")
                    handleWakeWordManagement(voiceTriggerViewModel.stateFlow.value)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        restModeManager = RestModeManager(this)
        
        voskManager = VoskWakeWordManager(this) {
            runOnUiThread {
                Log.d("MainActivity", "Wake Word Detected via Vosk")
                voiceTriggerViewModel.setTrigger(true)
            }
        }

        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenStateReceiver, screenFilter)

        setupImmersiveMode()
        setupViewPager()
        observeNanamyState()
    }

    fun onRestModeChanged() {
        runOnUiThread {
            Log.d("MainActivity", "onRestModeChanged | isRestActive=${restModeManager.isRestModeActive}")
            handleWakeWordManagement(voiceTriggerViewModel.stateFlow.value)
        }
    }

    private fun observeNanamyState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                voiceTriggerViewModel.stateFlow.collect { state ->
                    Log.d("MainActivity", "Nanamy State changed: $state")
                    handleWakeWordManagement(state)
                }
            }
        }
    }

    private fun handleWakeWordManagement(state: NanamyState) {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isScreenOn = powerManager.isInteractive

        val isRestActive = restModeManager.isRestModeActive
        val shouldListen = isScreenOn && isRestActive && repo.restModeHandsFreeEnabled

        if (!shouldListen) {
            voskManager.stop()
            return
        }

        if (state == NanamyState.IDLE) {
            if (hasMicrophonePermission()) {
                Log.d("MainActivity", "Hands-free active and screen ON, starting Vosk")
                voskManager.start()
            } else {
                requestMicrophonePermission()
            }
        } else {
            // Nanamy is busy. Release mic for Google/STT
            Log.d("MainActivity", "Nanamy busy ($state), stopping Vosk")
            voskManager.stop()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_L1) {
            val isRestActive = restModeManager.isRestModeActive
            if (!isRestActive && binding.viewPager.currentItem == 1) {
                if (event?.repeatCount == 0) {
                    voiceTriggerViewModel.setTrigger(true)
                }
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_L1) {
            val isRestActive = restModeManager.isRestModeActive
            if (!isRestActive && binding.viewPager.currentItem == 1) {
                voiceTriggerViewModel.setTrigger(false)
            }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        refreshVisualRotation()
    }

    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestMicrophonePermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Permission granted, logic will trigger on next state update or resume
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupImmersiveMode()
            refreshVisualRotation()
        }
    }

    override fun onStop() {
        super.onStop()
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) {
            Log.d("MainActivity", "onStop with screen OFF -> Stopping Vosk")
            voskManager.stop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: Exception) {}
        restModeManager.stopRestMode()
        voskManager.stop()
    }

    private fun setupImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = LauncherPagerAdapter(this)
        binding.viewPager.setCurrentItem(1, false)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            return
        }
        if (binding.viewPager.currentItem != 1) {
            binding.viewPager.setCurrentItem(1, true)
        }
    }
}
