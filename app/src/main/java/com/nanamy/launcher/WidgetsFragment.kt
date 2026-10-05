package com.nanamy.launcher

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.nanamy.launcher.databinding.FragmentWidgetsBinding

/**
 * Left-side launcher fragment holding vertical infinite-scroll widget ViewPager2.
 * Caches selected widget position in RAM memory so returning to the tab retains the last viewed widget.
 */
class WidgetsFragment : Fragment() {

    companion object {
        private var savedWidgetPosition: Int = -1
    }

    private var _binding: FragmentWidgetsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: WidgetsPagerAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWidgetsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = WidgetsPagerAdapter(this)
        binding.vpWidgets.apply {
            this.adapter = this@WidgetsFragment.adapter
            orientation = ViewPager2.ORIENTATION_VERTICAL
            offscreenPageLimit = 1

            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    savedWidgetPosition = position
                }
            })
        }
    }

    override fun onResume() {
        super.onResume()
        loadWidgets()
    }

    private fun loadWidgets() {
        val repo = (requireContext().applicationContext as NanamyApplication).settingsRepository
        val savedOrder = try {
            val arr = org.json.JSONArray(repo.widgetOrderJson)
            List(arr.length()) { arr.getString(it) }
        } catch (e: Exception) {
            listOf("MUSIC", "WEATHER", "CALENDAR", "NOTES", "MESSAGES")
        }

        val allAvailable = mutableMapOf(
            "MUSIC" to WidgetModel("1", "Música", Color.parseColor("#1DB954"), WidgetType.MUSIC),
            "WEATHER" to WidgetModel("2", "Clima", Color.parseColor("#00A3E0")),
            "CALENDAR" to WidgetModel("3", "Calendario", Color.parseColor("#D93025"), WidgetType.CALENDAR),
            "NOTES" to WidgetModel("4", "Notas", Color.parseColor("#F4B400"), WidgetType.NOTES),
            "MESSAGES" to WidgetModel("5", "Mensajes", Color.parseColor("#25D366"), WidgetType.MESSAGES)
        )

        val orderedList = savedOrder.mapNotNull { allAvailable[it] }
        adapter.setWidgets(orderedList)

        val realCount = adapter.getRealCount()
        if (realCount > 0) {
            if (savedWidgetPosition != -1 && savedWidgetPosition in 0 until WidgetsPagerAdapter.INFINITE_COUNT) {
                binding.vpWidgets.setCurrentItem(savedWidgetPosition, false)
            } else {
                val middleStart = (WidgetsPagerAdapter.INFINITE_COUNT / 2)
                val initialPosition = middleStart - (middleStart % realCount)
                savedWidgetPosition = initialPosition
                binding.vpWidgets.setCurrentItem(initialPosition, false)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
