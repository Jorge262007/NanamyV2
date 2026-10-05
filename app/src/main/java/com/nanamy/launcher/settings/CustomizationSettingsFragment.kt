package com.nanamy.launcher.settings

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.nanamy.launcher.SettingsActivity
import com.nanamy.launcher.databinding.FragmentSettingsCustomizationBinding
import java.util.Locale

/**
 * Settings fragment for customizing appearance, TTS voice pitch and speed, and personality prompts.
 */
class CustomizationSettingsFragment : Fragment() {

    private var _binding: FragmentSettingsCustomizationBinding? = null
    private val binding get() = _binding!!
    private val repo by lazy { (requireActivity() as SettingsActivity).repo }

    private var tempEyesColor: Int = Color.WHITE

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsCustomizationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        tempEyesColor = repo.eyesColor
        binding.viewEyesColorPreview.setBackgroundColor(tempEyesColor)
        binding.switchDarkMode.isChecked = repo.themeMode != AppCompatDelegate.MODE_NIGHT_NO

        val speed = repo.ttsSpeechRate.coerceIn(0.5f, 2.0f)
        val pitch = repo.ttsPitch.coerceIn(0.5f, 2.0f)

        binding.sliderTtsSpeed.value = speed
        binding.sliderTtsPitch.value = pitch

        binding.tvTtsSpeedLabel.text = "Voice Speed: ${String.format(Locale.US, "%.1fx", speed)}"
        binding.tvTtsPitchLabel.text = "Voice Pitch: ${String.format(Locale.US, "%.1fx", pitch)}"

        binding.etPersonaPrompt.setText(repo.userPersonaPrompt)
        binding.etLocalLlmPersonaPrompt.setText(repo.localLlmUserPersonaPrompt)
    }

    private fun setupListeners() {
        binding.btnEyesColor.setOnClickListener { showColorSelectionDialog() }

        binding.sliderTtsSpeed.addOnChangeListener { _, value, _ ->
            binding.tvTtsSpeedLabel.text = "Voice Speed: ${String.format(Locale.US, "%.1fx", value)}"
        }

        binding.sliderTtsPitch.addOnChangeListener { _, value, _ ->
            binding.tvTtsPitchLabel.text = "Voice Pitch: ${String.format(Locale.US, "%.1fx", value)}"
        }

        binding.btnSave.setOnClickListener { saveSettings() }
    }

    private fun showColorSelectionDialog() {
        val colors = linkedMapOf(
            "White" to Color.WHITE,
            "Blue" to Color.CYAN,
            "Green" to Color.GREEN,
            "Red" to Color.RED,
            "Yellow" to Color.YELLOW,
            "Purple" to Color.MAGENTA,
            "Custom (Hex)..." to -1
        )
        val names = colors.keys.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Pick Eyes Color")
            .setItems(names) { _, which ->
                val selectedName = names[which]
                val color = colors[selectedName] ?: Color.WHITE
                if (color == -1) {
                    showCustomColorDialog()
                } else {
                    applyEyesColor(color)
                }
            }
            .show()
    }

    private fun showCustomColorDialog() {
        val et = EditText(requireContext()).apply {
            setText(String.format("#%06X", (0xFFFFFF and tempEyesColor)))
            hint = "#RRGGBB"
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Custom Hex Color")
            .setView(et)
            .setPositiveButton("Apply") { _, _ ->
                try {
                    val color = Color.parseColor(et.text.toString())
                    applyEyesColor(color)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Invalid hex format", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyEyesColor(color: Int) {
        tempEyesColor = color
        binding.viewEyesColorPreview.setBackgroundColor(color)
    }

    private fun saveSettings() {
        repo.eyesColor = tempEyesColor
        repo.ttsSpeechRate = binding.sliderTtsSpeed.value
        repo.ttsPitch = binding.sliderTtsPitch.value
        repo.userPersonaPrompt = binding.etPersonaPrompt.text.toString()
        repo.localLlmUserPersonaPrompt = binding.etLocalLlmPersonaPrompt.text.toString()

        val newTheme = if (binding.switchDarkMode.isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        repo.themeMode = newTheme
        AppCompatDelegate.setDefaultNightMode(newTheme)

        Toast.makeText(requireContext(), "Customization saved", Toast.LENGTH_SHORT).show()
        parentFragmentManager.popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
