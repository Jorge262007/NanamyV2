package com.nanamy.launcher.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.nanamy.launcher.FileUtils
import com.nanamy.launcher.SettingsActivity
import com.nanamy.launcher.databinding.FragmentSettingsAdvancedBinding
import com.nanamy.launcher.databinding.ItemGeminiKeyBinding
import org.json.JSONArray
import org.json.JSONObject

class AdvancedSettingsFragment : Fragment() {

    private var _binding: FragmentSettingsAdvancedBinding? = null
    private val binding get() = _binding!!
    private val repo by lazy { (requireActivity() as SettingsActivity).repo }

    private val modelPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val path = FileUtils.getPathFromUri(requireContext(), it)
            if (path != null) {
                binding.tvModelPath.text = path
            } else {
                Toast.makeText(requireContext(), "Could not resolve file path", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsAdvancedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        loadGeminiKeys()
        binding.etSystemPrompt.setText(repo.systemPrompt)
        binding.etLocalLlmSystemPrompt.setText(repo.localLlmSystemPrompt)
        binding.switchLocalLlm.isChecked = repo.localLlmEnabled
        binding.tvModelPath.text = repo.localLlmModelPath.ifEmpty { "No model selected" }
        binding.llModelPicker.visibility = if (repo.localLlmEnabled) View.VISIBLE else View.GONE
    }

    private fun loadGeminiKeys() {
        binding.llKeysContainer.removeAllViews()
        try {
            val arr = JSONArray(repo.geminiKeysJson)
            for (i in 0 until arr.length()) {
                addKeyView(arr.getJSONObject(i))
            }
        } catch (e: Exception) {}
    }

    private fun addKeyView(json: JSONObject?) {
        val itemBinding = ItemGeminiKeyBinding.inflate(LayoutInflater.from(requireContext()), binding.llKeysContainer, false)
        
        json?.let {
            itemBinding.etKey.setText(it.optString("key"))
            itemBinding.cbInUse.isChecked = it.optBoolean("inUse", false)
            itemBinding.etKey.isEnabled = itemBinding.cbInUse.isChecked
        }

        itemBinding.cbInUse.setOnCheckedChangeListener { _, isChecked ->
            itemBinding.etKey.isEnabled = isChecked
        }

        itemBinding.btnRemoveKey.setOnClickListener {
            binding.llKeysContainer.removeView(itemBinding.root)
        }

        binding.llKeysContainer.addView(itemBinding.root)
    }

    private fun setupListeners() {
        binding.btnAddKey.setOnClickListener { addKeyView(null) }
        binding.switchLocalLlm.setOnCheckedChangeListener { _, isChecked ->
            binding.llModelPicker.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        binding.btnSelectModel.setOnClickListener { modelPickerLauncher.launch("*/*") }
        binding.btnSave.setOnClickListener { saveSettings() }
    }

    private fun saveSettings() {
        val keysArr = JSONArray()
        for (i in 0 until binding.llKeysContainer.childCount) {
            val child = binding.llKeysContainer.getChildAt(i)
            val itemBinding = ItemGeminiKeyBinding.bind(child)
            val obj = JSONObject()
            obj.put("key", itemBinding.etKey.text.toString())
            obj.put("inUse", itemBinding.cbInUse.isChecked)
            keysArr.put(obj)
        }
        repo.geminiKeysJson = keysArr.toString()
        repo.systemPrompt = binding.etSystemPrompt.text.toString()
        repo.localLlmSystemPrompt = binding.etLocalLlmSystemPrompt.text.toString()
        repo.localLlmEnabled = binding.switchLocalLlm.isChecked
        repo.localLlmModelPath = binding.tvModelPath.text.toString().let { if (it == "No model selected") "" else it }

        Toast.makeText(requireContext(), "Advanced settings saved", Toast.LENGTH_SHORT).show()
        parentFragmentManager.popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
