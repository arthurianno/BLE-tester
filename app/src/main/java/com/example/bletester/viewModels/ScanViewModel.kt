package com.example.bletester.viewModels

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
@SuppressLint("StaticFieldLeak")
class ScanViewModel @Inject constructor () : ViewModel() {

    var deviceList by mutableStateOf<List<BluetoothDevice>>(emptyList())
        private set
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val bluetoothLeScanner = adapter?.bluetoothLeScanner
    private val settings: ScanSettings
    private val filters: List<ScanFilter>
    private var scanning = false
    private val handler = android.os.Handler()
    private val SCAN_PERIOD: Long = 10000


    init {
        settings = buildSettings()
        filters = buildFilter()
    }

    private fun buildSettings() =
        ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

    private fun buildFilter() =
        listOf(
            ScanFilter.Builder()
                .build()
        )

    @SuppressLint("MissingPermission")
    fun scanLeDevice(startRange: Long, endRange: Long) {
        if (!scanning) {
            handler.postDelayed({
                scanning = false
                bluetoothLeScanner?.stopScan(leScanCallback(startRange, endRange))
            }, SCAN_PERIOD)
            scanning = true
            bluetoothLeScanner?.startScan(leScanCallback(startRange, endRange))
        } else {
            scanning = false
            bluetoothLeScanner?.stopScan(leScanCallback(startRange, endRange))
        }
    }

    private fun leScanCallback(startRange: Long, endRange: Long) = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val device = result.device
            val deviceName = device.name ?: return // If device name is null, skip
            val startRangeLastFourDigits = startRange.toString().takeLast(4)
            val endRangeLastFourDigits = endRange.toString().takeLast(4)


            // Check if device name contains "Sat"
            if (deviceName.contains("Satellite")) {
                val lastFourDigits = deviceName.takeLast(4)
                val satelliteNumber = lastFourDigits.toIntOrNull()

                // Check if last four digits are valid numbers
                if (satelliteNumber != null) {
                    // Check if satellite number is within the specified range
                    if (satelliteNumber.toString() in startRangeLastFourDigits..endRangeLastFourDigits) {
                        if(!deviceList.any{it.address == device.address}){
                            Log.e("ScanViewModel", "device $deviceName")
                            deviceList = deviceList + listOf(device)
                        }

                    }
                }
            }
        }
    }
    fun clearData() {
        deviceList = emptyList()
    }
}