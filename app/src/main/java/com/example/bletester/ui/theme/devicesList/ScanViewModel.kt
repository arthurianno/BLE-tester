package com.example.bletester.ui.theme.devicesList

import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bletester.services.ScanningService
import com.example.bletester.utils.SharedData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanningService: ScanningService,
    val sharedData: SharedData
) : ViewModel() {

    val toastMessage = scanningService.toastMessage
    val foundDevices = scanningService.foundDevices
    val unCheckedDevices = scanningService.unCheckedDevices
    val checkedDevices = scanningService.checkedDevicesUi
    val scanning = scanningService.scanning
    val progress = MutableStateFlow(0f)
    private val deviceTypeLetter = scanningService.deviceTypeLetter

    private val _selectedDeviceType = MutableStateFlow("SatelliteOnline")
    val selectedDeviceType: StateFlow<String> = _selectedDeviceType.asStateFlow()

    private val _currentDeviceType = MutableStateFlow("")
    val currentDeviceType: StateFlow<String> = _currentDeviceType.asStateFlow()

    private val _startRange = MutableStateFlow(0L)
    val startRange: StateFlow<Long> = _startRange.asStateFlow()

    private val _endRange = MutableStateFlow(0L)
    val endRange: StateFlow<Long> = _endRange.asStateFlow()

    private val _isStartRangeValid = MutableStateFlow(true)
    val isStartRangeValid: StateFlow<Boolean> = _isStartRangeValid.asStateFlow()

    private val _isEndRangeValid = MutableStateFlow(true)
    val isEndRangeValid: StateFlow<Boolean> = _isEndRangeValid.asStateFlow()

    val totalDevices: StateFlow<Int> = combine(_startRange, _endRange) { start, end ->
        if (end > start) (end - start + 1).toInt() else 0
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        viewModelScope.launch {
            deviceTypeLetter.collectLatest { letter ->
                updateSelectedDeviceType(letter)
            }
        }
    }

    fun updateSelectedDeviceType(letter: String) {
        _selectedDeviceType.value = when (letter) {
            "D" -> "SatelliteOnline"
            "E" -> "SatelliteVoice"
            "F" -> "AnotherDevice"
            else -> _selectedDeviceType.value
        }
        updateCurrentDeviceType()
    }

    private fun updateCurrentDeviceType() {
        _currentDeviceType.value = when (_selectedDeviceType.value) {
            "SatelliteOnline" -> "D"
            "SatelliteVoice" -> "E"
            "AnotherDevice" -> "F"
            else -> ""
        }
    }

    fun setStartRange(value: String) {
        val longValue = value.toLongOrNull() ?: 0
        _startRange.value = longValue
        _isStartRangeValid.value = value.matches(Regex("^\\d{10}$"))
    }

    fun setEndRange(value: String) {
        val longValue = value.toLongOrNull() ?: 0
        _endRange.value = longValue
        _isEndRangeValid.value = value.matches(Regex("^\\d{10}$"))
    }

    fun scanLeDevice() {
        scanningService.scanLeDevice(_currentDeviceType.value, _startRange.value, _endRange.value)
        scanning.value = true
    }

    fun stopScanning() {
        scanningService.stopScanning()
        Log.e("ScanViewModel","stopScanning from ScanViewModel")
    }

    fun updateReportViewModel(command: String) {
        scanningService.updateReportViewModel(command)
    }
}