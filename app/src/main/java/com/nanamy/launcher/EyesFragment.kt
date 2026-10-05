package com.nanamy.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nanamy.launcher.notes.NotesViewModel
import com.nanamy.launcher.calendar.CalendarViewModel
import com.nanamy.launcher.databinding.FragmentEyesBinding
import com.nanamy.launcher.voice.VoiceAssistantManager
import com.nanamy.launcher.voice.VoiceTriggerViewModel
import kotlinx.coroutines.launch

/**
 * Fragmento central: Contenedor para el EyeFaceView y punto de entrada para el asistente de voz.
 */
class EyesFragment : Fragment() {

    private var _binding: FragmentEyesBinding? = null
    private val binding get() = _binding!!

    private lateinit var voiceAssistantManager: VoiceAssistantManager
    private val voiceTriggerViewModel: VoiceTriggerViewModel by activityViewModels()
    private val calendarViewModel: CalendarViewModel by activityViewModels()
    private val notesViewModel: NotesViewModel by activityViewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) {
            voiceAssistantManager.startListening()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEyesBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        voiceAssistantManager = VoiceAssistantManager(requireContext().applicationContext, calendarViewModel, notesViewModel) { state ->
            binding.eyeFaceView.setState(state)
            voiceTriggerViewModel.setState(state)
        }

        android.util.Log.d("EyesFragment", "Passing VoiceAssistantManager to RestModeManager")
        (activity as? MainActivity)?.restModeManager?.setVoiceAssistantManager(voiceAssistantManager)

        setupVoiceTriggerObserver()
    }

    private fun setupVoiceTriggerObserver() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                voiceTriggerViewModel.triggerFlow.collect { active ->
                    if (active) {
                        checkPermissionAndStartListening()
                    } else {
                        voiceAssistantManager.stopListening()
                    }
                }
            }
        }
    }

    private fun checkPermissionAndStartListening() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            voiceAssistantManager.startListening()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onResume() {
        super.onResume()
        voiceAssistantManager.onAssistantFocused()
    }

    override fun onPause() {
        super.onPause()
        voiceAssistantManager.onAssistantBlurred()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        voiceAssistantManager.destroy()
        _binding = null
    }
}
