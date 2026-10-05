package com.nanamy.launcher.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nanamy.launcher.R
import com.nanamy.launcher.SettingsActivity
import com.nanamy.launcher.databinding.FragmentSettingsMainBinding

class SettingsMainFragment : Fragment() {

    private var _binding: FragmentSettingsMainBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        val categories = listOf(
            Category("Customization", "Prompts, Eye Color, Theme", android.R.drawable.ic_menu_gallery),
            Category("Widgets", "Enable and Reorder widgets", android.R.drawable.ic_menu_sort_by_size),
            Category("Voice & Rest", "Wake Word, Hands-Free, Volume", android.R.drawable.ic_btn_speak_now),
            Category("Advanced", "API Keys, Local LLM settings", android.R.drawable.ic_menu_info_details),
            Category("About", "App info and version", android.R.drawable.ic_menu_help)
        )

        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = CategoryAdapter(categories) { category ->
                handleCategoryClick(category)
            }
        }
    }

    private fun handleCategoryClick(category: Category) {
        val fragment = when (category.title) {
            "Customization" -> CustomizationSettingsFragment()
            "Widgets" -> WidgetSettingsFragment()
            "Voice & Rest" -> VoiceSettingsFragment()
            "Advanced" -> AdvancedSettingsFragment()
            "About" -> AboutSettingsFragment()
            else -> null
        }
        fragment?.let { (activity as? SettingsActivity)?.showFragment(it, true) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class Category(val title: String, val summary: String, val iconRes: Int)

    private class CategoryAdapter(
        private val items: List<Category>,
        private val onClick: (Category) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.ivIcon)
            val title: TextView = view.findViewById(R.id.tvTitle)
            val summary: TextView = view.findViewById(R.id.tvSummary)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_settings_category, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.title.text = item.title
            holder.summary.text = item.summary
            holder.icon.setImageResource(item.iconRes)
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
