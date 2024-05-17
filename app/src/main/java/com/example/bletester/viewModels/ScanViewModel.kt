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
import com.example.bletester.EntireCheck
import com.example.bletester.ble.BleCallbackEvent
import com.example.bletester.ble.BleControlManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.LinkedList
import java.util.Queue
import javax.inject.Inject


@HiltViewModel
@SuppressLint("StaticFieldLeak")
class ScanViewModel @Inject constructor (val bleControlManager: BleControlManager) : ViewModel() {

     var deviceQueue: Queue<BluetoothDevice> = LinkedList()
    var isQueueEmpty by mutableStateOf(deviceQueue.isEmpty())
        private set
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val bluetoothLeScanner = adapter?.bluetoothLeScanner
    private val settings: ScanSettings
    private val filters: List<ScanFilter>
    private var scanning = false
    private val handler = android.os.Handler()
    private val SCAN_PERIOD: Long = 10000
    private var isConnecting = false



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
                    if(!deviceQueue.any{it.address == device.address}){
                        Log.e("ScanViewModel", "device $deviceName")
                        deviceQueue.add(device)
                        if(!isConnecting) {
                            connectToDeviceSequentially()
                        }
                        bleControlManager.setBleCallbackEvent(object :BleCallbackEvent{
                            override fun onHandleCheck() {
                                isConnecting = false

                            }
                        })
                    }
                }
            }
        }
    }


    @SuppressLint("MissingPermission")
    fun connectToDeviceSequentially(){
        if(!isConnecting && deviceQueue.isNotEmpty()) {
            isConnecting = true
            val device = deviceQueue.poll()

            device?.let {
                bleControlManager.connect(it)
                    .done {
                        Log.d("BleControlManager", "Подключено к устройству ${device.name}")
                        bleControlManager.sendPinCommand("master",EntireCheck.PIN_C0DE)
                    }
                    .fail { device, status ->
                        isConnecting = false
                        Log.e(
                            "BleControlManager",
                            "Не удалось подключиться к устройству ${device.name}: $status"

                        )
                        connectToDeviceSequentially()
                    }
                    .enqueue()
            }
        }else{
            Log.d("BleControlManager", "Все устройства обработаны")
        }
    }
    fun clearData() {
        deviceQueue.clear()

    }

    fun onDeviceProcessed() {
        isConnecting = false
        connectToDeviceSequentially()
    }

    companion object{
        val entireCheckQueue: Queue<EntireCheck> = LinkedList()
    }

}