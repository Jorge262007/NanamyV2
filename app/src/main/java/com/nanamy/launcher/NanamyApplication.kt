package com.nanamy.launcher

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.nanamy.launcher.voice.NanamyVoiceConfig

class NanamyApplication : Application() {
    
    lateinit var settingsRepository: NanamySettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = NanamySettingsRepository(this)
        NanamyVoiceConfig.initialize(settingsRepository)
        
        // Initialize Home directory structure
        FileUtils.initHomeDirectory(this)
        
        // Apply saved theme mode
        AppCompatDelegate.setDefaultNightMode(settingsRepository.themeMode)
    }
}
