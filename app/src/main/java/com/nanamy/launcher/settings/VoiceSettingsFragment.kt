package com.nanamy.launcher.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.nanamy.launcher.SettingsActivity
import com.nanamy.launcher.databinding.FragmentSettingsVoiceBinding

class VoiceSettingsFragment : Fragment() {

    private var _binding: FragmentSettingsVoiceBinding? = null
    private val binding get() = _binding!!
    private val repo by lazy { (requireActivity() as SettingsActivity).repo }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsVoiceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        binding.switchRestHandsFree.isChecked = repo.restModeHandsFreeEnabled
        binding.etRestHotword.setText(repo.restModeHotword)
        binding.sliderRestVolume.value = repo.restModeVolume.toFloat()
        binding.tvRestVolumeLabel.text = "Rest Mode Volume: ${repo.restModeVolume}%"
    }

    private fun setupListeners() {
        binding.sliderRestVolume.addOnChangeListener { _, value, _ ->
            binding.tvRestVolumeLabel.text = "Rest Mode Volume: ${value.toInt()}%"
        }
        binding.btnSave.setOnClickListener { saveSettings() }
    }

    private fun saveSettings() {
        repo.restModeHandsFreeEnabled = binding.switchRestHandsFree.isChecked
        repo.restModeHotword = binding.etRestHotword.text.toString()
        repo.restModeVolume = binding.sliderRestVolume.value.toInt()

        Toast.makeText(requireContext(), "Voice settings saved", Toast.LENGTH_SHORT).show()
        parentFragmentManager.popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
