package com.nanamy.launcher.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nanamy.launcher.eyes.NanamyState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VoiceTriggerViewModel : ViewModel() {
    private val _triggerFlow = MutableSharedFlow<Boolean>()
    val triggerFlow = _triggerFlow.asSharedFlow()

    private val _stateFlow = MutableStateFlow(NanamyState.IDLE)
    val stateFlow = _stateFlow.asStateFlow()

    fun setTrigger(active: Boolean) {
        viewModelScope.launch {
            _triggerFlow.emit(active)
        }
    }

    fun setState(state: NanamyState) {
        viewModelScope.launch {
            _stateFlow.value = state
        }
    }
}
