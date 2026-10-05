package com.nanamy.launcher.calendar

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class CalendarViewModel : ViewModel() {
    private val _calendarUpdateFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val calendarUpdateFlow = _calendarUpdateFlow.asSharedFlow()

    fun notifyCalendarChanged() {
        _calendarUpdateFlow.tryEmit(Unit)
    }
}
