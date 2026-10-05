package com.nanamy.launcher.widgets

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nanamy.launcher.calendar.CalendarAlarmScheduler
import com.nanamy.launcher.calendar.CalendarEntry
import com.nanamy.launcher.calendar.CalendarFileParser
import com.nanamy.launcher.calendar.CalendarViewModel
import com.nanamy.launcher.databinding.DialogEditCalendarEventBinding
import com.nanamy.launcher.databinding.FragmentCalendarWidgetBinding
import com.nanamy.launcher.databinding.ItemCalendarDayBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class CalendarWidgetFragment : Fragment() {

    private var _binding: FragmentCalendarWidgetBinding? = null
    private val binding get() = _binding!!
    private lateinit var calendarParser: CalendarFileParser
    private lateinit var alarmScheduler: CalendarAlarmScheduler
    private val calendarViewModel: CalendarViewModel by activityViewModels()
    private var currentMonth = YearMonth.now()
    private var isAddMode = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCalendarWidgetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        calendarParser = CalendarFileParser(requireContext())
        alarmScheduler = CalendarAlarmScheduler(requireContext())
        
        setupNavigation()
        setupObserver()
        renderMonth()
    }

    private fun setupNavigation() {
        binding.btnPrevMonth.setOnClickListener {
            currentMonth = currentMonth.minusMonths(1)
            renderMonth()
        }
        binding.btnNextMonth.setOnClickListener {
            currentMonth = currentMonth.plusMonths(1)
            renderMonth()
        }
        binding.btnAddEventMode.setOnClickListener {
            isAddMode = !isAddMode
            updateAddModeUI()
        }
    }

    private fun updateAddModeUI() {
        binding.btnAddEventMode.setColorFilter(if (isAddMode) 0xFF00BFA5.toInt() else 0xFFFFFFFF.toInt())
        if (isAddMode) {
            Toast.makeText(context, "Edit Mode Active: Tap a day to add/edit", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupObserver() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                calendarViewModel.calendarUpdateFlow.collect {
                    renderMonth()
                }
            }
        }
    }

    private fun renderMonth() {
        val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
        binding.tvMonthName.text = currentMonth.format(formatter)

        val daysInMonth = currentMonth.lengthOfMonth()
        val firstOfMonth = currentMonth.atDay(1)
        val dayOfWeek = firstOfMonth.dayOfWeek.value % 7 // 0 for Sunday

        val days = mutableListOf<LocalDate?>()
        for (i in 0 until dayOfWeek) {
            days.add(null)
        }
        for (i in 1..daysInMonth) {
            days.add(currentMonth.atDay(i))
        }

        // Get all entries but filter for display: only future or current day events
        val now = LocalDateTime.now()
        val allEvents = calendarParser.getEntriesInRange(currentMonth.atDay(1), currentMonth.atEndOfMonth())
        val visibleEvents = allEvents.filter { entry ->
            if (entry.allDay) {
                !entry.date.isBefore(now.toLocalDate())
            } else {
                val entryDateTime = entry.date.atTime(entry.time ?: LocalTime.MAX)
                !entryDateTime.isBefore(now)
            }
        }

        binding.rvCalendarGrid.apply {
            layoutManager = GridLayoutManager(requireContext(), 7)
            adapter = CalendarGridAdapter(days, visibleEvents) { date ->
                if (isAddMode) {
                    showAddEditEventDialog(date, visibleEvents.filter { it.date == date })
                    isAddMode = false
                    updateAddModeUI()
                } else {
                    showDayDetails(date, visibleEvents.filter { it.date == date })
                }
            }
        }
    }

    private fun showDayDetails(date: LocalDate, dayEvents: List<CalendarEntry>) {
        if (dayEvents.isEmpty()) {
            Toast.makeText(context, "No more events for today!", Toast.LENGTH_SHORT).show()
            return
        }
        
        val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)
        val msg = dayEvents.joinToString("\n\n") { entry ->
            val time = if (entry.allDay) "All Day" else entry.time?.format(timeFormatter) ?: "All Day"
            "[$time] ${entry.title}"
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(date.format(DateTimeFormatter.ofPattern("EEEE, MMM dd", Locale.ENGLISH)))
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .create()
            
        dialog.window?.setWindowAnimations(android.R.style.Animation_Dialog)
        dialog.show()
    }

    private fun showAddEditEventDialog(date: LocalDate, existingEvents: List<CalendarEntry>) {
        if (existingEvents.isNotEmpty()) {
            val options = mutableListOf<String>()
            options.add("+ Add New Event")
            existingEvents.forEach { options.add("Edit: ${it.title}") }

            AlertDialog.Builder(requireContext())
                .setTitle("Select Action for $date")
                .setItems(options.toTypedArray()) { _, which ->
                    if (which == 0) {
                        openEventForm(date, null)
                    } else {
                        openEventForm(date, existingEvents[which - 1])
                    }
                }
                .show()
        } else {
            openEventForm(date, null)
        }
    }

    private fun openEventForm(date: LocalDate, entry: CalendarEntry?) {
        val dialogBinding = DialogEditCalendarEventBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        var selectedTime = entry?.time ?: LocalTime.of(12, 0)
        var selectedStartDate = entry?.recurringStart ?: date
        var selectedEndDate = entry?.recurringEnd ?: date.plusMonths(1)

        // Initialize fields
        entry?.let {
            dialogBinding.etTitle.setText(it.title)
            dialogBinding.cbAllDay.isChecked = it.allDay
            dialogBinding.cbRemind.isChecked = it.remind
            dialogBinding.cbNotifyBefore.isChecked = it.notifyBeforeMinutes > 0
            if (it.notifyBeforeMinutes > 0) {
                dialogBinding.etNotifyMinutes.setText(it.notifyBeforeMinutes.toString())
                dialogBinding.etNotifyMinutes.isEnabled = true
            }
            if (it.isRecurring) {
                dialogBinding.llRecurringDates.visibility = View.VISIBLE
            }
            dialogBinding.btnDelete.visibility = View.VISIBLE
        }

        val updateTimeLabel = {
            val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)
            dialogBinding.tvTime.text = selectedTime.format(timeFormatter)
            dialogBinding.tvTime.isEnabled = !dialogBinding.cbAllDay.isChecked
        }
        updateTimeLabel()

        val updateDateLabels = {
            dialogBinding.tvStartDate.text = "Start: $selectedStartDate"
            dialogBinding.tvEndDate.text = "End: $selectedEndDate"
        }
        updateDateLabels()

        // Listeners
        dialogBinding.tvTime.setOnClickListener {
            TimePickerDialog(requireContext(), { _, h, m ->
                selectedTime = LocalTime.of(h, m)
                updateTimeLabel()
            }, selectedTime.hour, selectedTime.minute, false).show()
        }

        dialogBinding.cbAllDay.setOnCheckedChangeListener { _, isChecked ->
            dialogBinding.tvTime.isEnabled = !isChecked
            dialogBinding.tvTime.alpha = if (isChecked) 0.5f else 1.0f
        }

        dialogBinding.cbRecurring.setOnCheckedChangeListener { _, isChecked ->
            dialogBinding.llRecurringDates.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        dialogBinding.tvStartDate.setOnClickListener {
            DatePickerDialog(requireContext(), { _, y, m, d ->
                selectedStartDate = LocalDate.of(y, m + 1, d)
                updateDateLabels()
            }, selectedStartDate.year, selectedStartDate.monthValue - 1, selectedStartDate.dayOfMonth).show()
        }

        dialogBinding.tvEndDate.setOnClickListener {
            DatePickerDialog(requireContext(), { _, y, m, d ->
                selectedEndDate = LocalDate.of(y, m + 1, d)
                updateDateLabels()
            }, selectedEndDate.year, selectedEndDate.monthValue - 1, selectedEndDate.dayOfMonth).show()
        }

        dialogBinding.cbNotifyBefore.setOnCheckedChangeListener { _, isChecked ->
            dialogBinding.etNotifyMinutes.isEnabled = isChecked
        }

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnDelete.setOnClickListener {
            entry?.let {
                calendarParser.deleteEntry(it.title, if (it.isRecurring) it.recurringDay!! else it.date.toString())
                calendarViewModel.notifyCalendarChanged()
            }
            dialog.dismiss()
        }

        dialogBinding.btnSave.setOnClickListener {
            val title = dialogBinding.etTitle.text.toString()
            if (title.isBlank()) {
                dialogBinding.tilTitle.error = "Title required"
                return@setOnClickListener
            }

            val allday = dialogBinding.cbAllDay.isChecked
            val remind = dialogBinding.cbRemind.isChecked
            val recurring = dialogBinding.cbRecurring.isChecked
            val notifyMinutes = if (dialogBinding.cbNotifyBefore.isChecked) {
                dialogBinding.etNotifyMinutes.text.toString().toIntOrNull() ?: 0
            } else 0

            val newEntry = if (recurring) {
                CalendarEntry(
                    title = title,
                    date = LocalDate.MIN,
                    time = if (allday) null else selectedTime,
                    allDay = allday,
                    remind = remind,
                    notifyBeforeMinutes = notifyMinutes,
                    isRecurring = true,
                    recurringDay = date.dayOfWeek.toString().lowercase().capitalize(),
                    recurringStart = selectedStartDate,
                    recurringEnd = selectedEndDate
                )
            } else {
                CalendarEntry(
                    title = title,
                    date = date,
                    time = if (allday) null else selectedTime,
                    allDay = allday,
                    remind = remind,
                    notifyBeforeMinutes = notifyMinutes,
                    isRecurring = false
                )
            }

            // If editing, delete old first
            entry?.let {
                calendarParser.deleteEntry(it.title, if (it.isRecurring) it.recurringDay!! else it.date.toString())
            }

            if (recurring) calendarParser.appendRecurring(newEntry)
            else calendarParser.appendEvent(newEntry)

            alarmScheduler.scheduleAlarm(newEntry)
            calendarViewModel.notifyCalendarChanged()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun String.capitalize() = this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class CalendarGridAdapter(
        private val days: List<LocalDate?>,
        private val events: List<CalendarEntry>,
        private val onDayClick: (LocalDate) -> Unit
    ) : RecyclerView.Adapter<CalendarGridAdapter.DayViewHolder>() {

        inner class DayViewHolder(val binding: ItemCalendarDayBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
            val binding = ItemCalendarDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return DayViewHolder(binding)
        }

        override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
            val date = days[position]
            if (date == null) {
                holder.binding.root.visibility = View.INVISIBLE
            } else {
                holder.binding.root.visibility = View.VISIBLE
                holder.binding.tvDayNumber.text = date.dayOfMonth.toString()
                
                val dayEvents = events.filter { it.date == date }
                
                holder.binding.vIndicatorRemind.visibility = if (dayEvents.any { it.remind }) View.VISIBLE else View.GONE
                holder.binding.vIndicatorRecurring.visibility = if (dayEvents.any { it.isRecurring }) View.VISIBLE else View.GONE
                holder.binding.vIndicatorNormal.visibility = if (dayEvents.any { !it.remind && !it.isRecurring }) View.VISIBLE else View.GONE

                if (date == LocalDate.now()) {
                    holder.binding.root.setCardBackgroundColor(0xFF00BFA5.toInt().let { 
                        (0x33 shl 24) or (it and 0x00FFFFFF)
                    })
                    holder.binding.tvDayNumber.setTextColor(0xFF00BFA5.toInt())
                    holder.binding.tvDayNumber.paint.isFakeBoldText = true
                } else {
                    holder.binding.root.setCardBackgroundColor(0xFF1A1A1A.toInt())
                    holder.binding.tvDayNumber.setTextColor(0xFFFFFFFF.toInt())
                    holder.binding.tvDayNumber.paint.isFakeBoldText = false
                }

                holder.binding.root.setOnClickListener { onDayClick(date) }
            }
        }

        override fun getItemCount(): Int = days.size
    }
}
