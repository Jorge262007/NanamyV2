package com.nanamy.launcher.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nanamy.launcher.SettingsActivity
import com.nanamy.launcher.databinding.FragmentSettingsWidgetsBinding
import com.nanamy.launcher.databinding.ItemWidgetConfigBinding
import org.json.JSONArray
import java.util.Collections

/**
 * Settings fragment for configuring enabled state and drag-to-reorder for widgets.
 */
class WidgetSettingsFragment : Fragment() {

    private var _binding: FragmentSettingsWidgetsBinding? = null
    private val binding get() = _binding!!
    private val repo by lazy { (requireActivity() as SettingsActivity).repo }

    private lateinit var currentOrder: MutableList<String>
    private lateinit var enabledStates: MutableMap<String, Boolean>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsWidgetsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadData()
        setupRecyclerView()
        binding.btnSave.setOnClickListener { saveChanges() }
    }

    private fun loadData() {
        currentOrder = try {
            val arr = JSONArray(repo.widgetOrderJson)
            MutableList(arr.length()) { arr.getString(it) }
        } catch (e: Exception) {
            mutableListOf("MUSIC", "WEATHER", "CALENDAR", "NOTES", "MESSAGES")
        }

        enabledStates = mutableMapOf(
            "MUSIC" to repo.widgetMusicEnabled,
            "WEATHER" to repo.widgetWeatherEnabled,
            "CALENDAR" to repo.widgetCalendarEnabled,
            "NOTES" to repo.widgetNotesEnabled,
            "MESSAGES" to repo.widgetMessagesEnabled
        )
    }

    private fun setupRecyclerView() {
        val adapter = WidgetConfigAdapter()
        binding.rvWidgetConfig.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION && fromPos != toPos) {
                    Collections.swap(currentOrder, fromPos, toPos)
                    adapter.notifyItemMoved(fromPos, toPos)
                }
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })

        itemTouchHelper.attachToRecyclerView(binding.rvWidgetConfig)
        adapter.itemTouchHelper = itemTouchHelper
    }

    private fun saveChanges() {
        repo.widgetOrderJson = JSONArray(currentOrder).toString()
        repo.widgetMusicEnabled = enabledStates["MUSIC"] ?: true
        repo.widgetWeatherEnabled = enabledStates["WEATHER"] ?: true
        repo.widgetCalendarEnabled = enabledStates["CALENDAR"] ?: true
        repo.widgetNotesEnabled = enabledStates["NOTES"] ?: true
        repo.widgetMessagesEnabled = enabledStates["MESSAGES"] ?: true

        Toast.makeText(requireContext(), "Widget configuration saved", Toast.LENGTH_SHORT).show()
        parentFragmentManager.popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class WidgetConfigAdapter : RecyclerView.Adapter<WidgetConfigAdapter.ViewHolder>() {

        var itemTouchHelper: ItemTouchHelper? = null

        inner class ViewHolder(val b: ItemWidgetConfigBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemWidgetConfigBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val type = currentOrder[position]
            holder.b.switchEnabled.text = type.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
            holder.b.switchEnabled.isChecked = enabledStates[type] ?: true

            holder.b.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                enabledStates[type] = isChecked
            }

            holder.b.ivDragHandle.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper?.startDrag(holder)
                }
                false
            }

            holder.b.btnMoveUp.isEnabled = position > 0
            holder.b.btnMoveDown.isEnabled = position < currentOrder.size - 1

            holder.b.btnMoveUp.setOnClickListener {
                if (position > 0) {
                    Collections.swap(currentOrder, position, position - 1)
                    notifyItemMoved(position, position - 1)
                }
            }
            holder.b.btnMoveDown.setOnClickListener {
                if (position < currentOrder.size - 1) {
                    Collections.swap(currentOrder, position, position + 1)
                    notifyItemMoved(position, position + 1)
                }
            }
        }

        override fun getItemCount() = currentOrder.size
    }
}
