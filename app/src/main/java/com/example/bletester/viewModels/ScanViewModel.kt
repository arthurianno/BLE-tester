package com.example.bletester.viewModels

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.bletester.EntireCheck
import com.example.bletester.ble.BleCallbackEvent
import com.example.bletester.ble.BleControlManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.LinkedList
import java.util.Queue
import javax.inject.Inject


@Suppress("DEPRECATION")
@HiltViewModel
@SuppressLint("StaticFieldLeak")
class ScanViewModel @Inject constructor (val bleControlManager: BleControlManager) : ViewModel() {

     var deviceQueue: Queue<BluetoothDevice> = LinkedList()
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val bluetoothLeScanner = adapter?.bluetoothLeScanner
    private val settings: ScanSettings
    private val filters: List<ScanFilter>
    private var scanning = false
    private val handler = android.os.Handler()
    private val scanPeriod: Long = 10000
    private var isConnecting = false
    private val processedDevices = mutableSetOf<String>()



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
    fun scanLeDevice() {
        if (!scanning) {
            handler.postDelayed({
                scanning = false
                bluetoothLeScanner?.stopScan(leScanCallback())
            }, scanPeriod)
            scanning = true
            bluetoothLeScanner?.startScan(leScanCallback())
        } else {
            scanning = false
            bluetoothLeScanner?.stopScan(leScanCallback())
        }
    }

    private fun leScanCallback() = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val device = result.device
            val deviceName = device.name ?: return // If device name is null, skip

            // List of last four digits to check
            val validLastFourDigits = listOf("0001", "0002", "0325")

            // Check if device name contains "Sat"
            if (deviceName.contains("Satellite")) {
                val lastFourDigits = deviceName.takeLast(4)

                // Check if last four digits are in the valid list
                if (lastFourDigits in validLastFourDigits) {
                    // Check if device is not already processed
                    if (!processedDevices.contains(device.address)) {
                        Log.e("ScanViewModel", "device $deviceName")
                        deviceQueue.add(device)
                        if(!isConnecting) {
                            connectToDeviceSequentially()
                        }
                        bleControlManager.setBleCallbackEvent(object :BleCallbackEvent{
                            override fun onHandleCheck() {
                                isConnecting = false
                            }

                            override fun onVersionCheck(version: String) {
                                if(version == lastFourDigits){
                                    Log.e("ScanViewModel","Серийный номер устройства сходится!")
                                }else{
                                    Log.e("ScanViewModel","Серийный номер устройства не сходится!")
                                }
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
                processedDevices.add(it.address)
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

    companion object{
        val entireCheckQueue: Queue<EntireCheck> = LinkedList()
    }

}