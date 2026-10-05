package com.nanamy.launcher.notes

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class NotesViewModel : ViewModel() {
    private val _notesUpdateFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val notesUpdateFlow = _notesUpdateFlow.asSharedFlow()

    fun notifyNotesChanged() {
        _notesUpdateFlow.tryEmit(Unit)
    }
}
