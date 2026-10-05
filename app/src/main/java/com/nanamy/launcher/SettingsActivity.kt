package com.nanamy.launcher

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.nanamy.launcher.databinding.ActivitySettingsBinding
import com.nanamy.launcher.settings.*

class SettingsActivity : NanamyBaseActivity() {

    private lateinit var binding: ActivitySettingsBinding
    val repo by lazy { (application as NanamyApplication).settingsRepository }

    override val rootRotationView: View get() = binding.settingsRoot

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        if (savedInstanceState == null) {
            showFragment(SettingsMainFragment())
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
            } else {
                finish()
            }
        }
        
        supportFragmentManager.addOnBackStackChangedListener {
            val isMain = supportFragmentManager.backStackEntryCount == 0
            binding.toolbar.title = if (isMain) "Nanamy Settings" else getCurrentFragmentTitle()
        }
    }

    private fun getCurrentFragmentTitle(): String {
        val fragment = supportFragmentManager.findFragmentById(R.id.settingsContainer)
        return when (fragment) {
            is CustomizationSettingsFragment -> "Customization"
            is AdvancedSettingsFragment -> "Advanced"
            is WidgetSettingsFragment -> "Widgets"
            is VoiceSettingsFragment -> "Voice & Rest"
            is AboutSettingsFragment -> "About"
            else -> "Settings"
        }
    }

    fun showFragment(fragment: Fragment, addToBackStack: Boolean = false) {
        val transaction = supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in, 
                android.R.anim.fade_out,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            .replace(R.id.settingsContainer, fragment)
        if (addToBackStack) transaction.addToBackStack(null)
        transaction.commit()
    }
}
