package com.nanamy.launcher

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.nanamy.launcher.widgets.CalendarWidgetFragment
import com.nanamy.launcher.widgets.MusicWidgetFragment
import com.nanamy.launcher.widgets.NotesWidgetFragment
import com.nanamy.launcher.widgets.MessagesWidgetFragment

/**
 * Adapter for vertical ViewPager2 supporting infinite vertical scrolling.
 */
class WidgetsPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    private val settingsRepository by lazy {
        (fragment.requireContext().applicationContext as NanamyApplication).settingsRepository
    }

    private var widgets: List<WidgetModel> = emptyList()

    companion object {
        const val INFINITE_COUNT = 10000
    }

    fun setWidgets(allWidgets: List<WidgetModel>) {
        widgets = allWidgets.filter { widget ->
            when (widget.type) {
                WidgetType.MUSIC -> settingsRepository.widgetMusicEnabled
                WidgetType.CALENDAR -> settingsRepository.widgetCalendarEnabled
                WidgetType.NOTES -> settingsRepository.widgetNotesEnabled
                WidgetType.MESSAGES -> settingsRepository.widgetMessagesEnabled
                else -> {
                    when (widget.title) {
                        "Clima" -> settingsRepository.widgetWeatherEnabled
                        else -> true
                    }
                }
            }
        }
        notifyDataSetChanged()
    }

    fun getRealCount(): Int = widgets.size

    override fun getItemCount(): Int {
        return if (widgets.isEmpty()) 0 else INFINITE_COUNT
    }

    override fun getItemId(position: Int): Long {
        if (widgets.isEmpty()) return position.toLong()
        val realPosition = position % widgets.size
        return (widgets[realPosition].id.hashCode().toLong() shl 16) or (position.toLong() and 0xFFFF)
    }

    override fun containsItem(itemId: Long): Boolean = true

    override fun createFragment(position: Int): Fragment {
        if (widgets.isEmpty()) return WidgetPageFragment.newInstance("Empty", 0xFF000000.toInt())
        val realPosition = position % widgets.size
        val widget = widgets[realPosition]
        return when (widget.type) {
            WidgetType.MUSIC -> MusicWidgetFragment()
            WidgetType.CALENDAR -> CalendarWidgetFragment()
            WidgetType.NOTES -> NotesWidgetFragment()
            WidgetType.MESSAGES -> MessagesWidgetFragment()
            else -> WidgetPageFragment.newInstance(widget.title, widget.backgroundColor)
        }
    }
}
