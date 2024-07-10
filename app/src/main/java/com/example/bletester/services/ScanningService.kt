package com.example.bletester.services

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.example.bletester.ble.BleCallbackEvent
import com.example.bletester.ble.BleControlManager
import com.example.bletester.core.DeviceProcessor
import com.example.bletester.core.EntireCheck
import com.example.bletester.ui.theme.log.Logger
import com.example.bletester.ui.theme.report.ReportViewModel
import com.example.bletester.utils.FileModifyEvent
import com.example.bletester.utils.SharedData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject

private const val TAG = "ScanningService"
private const val MAX_CONNECTIONS = 5

@Suppress("DEPRECATION")
class ScanningService @Inject constructor(
    private val context: Context,
    reportViewModel: ReportViewModel,
    private val deviceProcessor: DeviceProcessor,
    private val sharedData: SharedData
) : FileModifyEvent {

    val toastMessage = MutableStateFlow<String?>(null)
    private val deviceQueue = ConcurrentLinkedQueue<BluetoothDevice>()
    private var deviceQueueProcessed: Queue<BluetoothDevice> = LinkedList()
    var foundDevices = mutableStateListOf<BluetoothDevice>()
    val unCheckedDevices = mutableStateListOf<BluetoothDevice>()
    val checkedDevices = mutableStateListOf<BluetoothDevice>()
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val bluetoothLeScanner = adapter?.bluetoothLeScanner
    private val settings: ScanSettings
    private val filters: List<ScanFilter>
    var scanning = MutableStateFlow(false)
    private var errorMessage: String? = null
    private var stopRequested = false
    private var startR: Long = 0L
    private var endR: Long = 0L
    private var diffRanges: Int? = 0
    private var counter = 0
    var bannedDevices = mutableStateListOf<BluetoothDevice>()
    private var letter = ""
    private var isFirstConnect = true
    private var isScanning: Boolean = false
    val deviceTypeLetter = MutableStateFlow("")

    private val deviceTypeToLetter = mapOf(
        "Online" to "D",
        "Voice" to "E",
        "AnotherDevice" to "F"
    )
    private val deviceTypeToLetterToReport = mapOf(
        "D" to "Online",
        "E" to "Voice",
        "F" to "AnotherDevices"
    )

    private val bleControlManagers = mutableMapOf<String, BleControlManager>()

    init {
        reportViewModel.registerCallback(this)
        settings = buildSettings()
        filters = buildFilter()
    }

    private fun buildSettings() =
        ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

    private fun buildFilter() =
        listOf(
            ScanFilter.Builder()
                .build()
        )
    @SuppressLint("MissingPermission")
    fun scanLeDevice(letter: String, start: Long, end: Long) {
        Logger.i(TAG, "Scanning started for device type: $letter, range: $start - $end")
        val typeOfLetterForReport = deviceTypeToLetterToReport[letter]
        sharedData.addressRange.value = Pair(start.toString(), end.toString())
        Log.d(TAG, "Address range: ${Pair(start, end)}")
        sharedData.typeOfDevice.value = typeOfLetterForReport
        Log.d(TAG, "Device type for report: $typeOfLetterForReport")
        toastMessage.value = "Сканирование!"
        startR = start
        endR = end
        deviceQueue.clear()
        foundDevices.clear()
        unCheckedDevices.clear()
        checkedDevices.clear()
        deviceQueueProcessed.clear()
        bannedDevices.clear()
        diffRanges = (end - start + 1).toInt()
        counter = diffRanges as Int
        this.letter = letter
        deviceTypeLetter.value = letter
        if (!scanning.value) {
            stopRequested = false
            scanning.value = true
            bluetoothLeScanner?.startScan(filters, settings, leScanCallback())
                ?: Log.e(TAG, "BluetoothLeScanner is null")
            Log.i(TAG, "Scanning started")
            Logger.i(TAG, "Scanning started")
        } else {
            Log.w(TAG, "Scanning is already in progress")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        Logger.i(TAG, "Stopping scan and disconnecting all devices")
        toastMessage.value = "Stop"
        isScanning = false
        stopRequested = true
        scanning.value = false
        letter = ""
        isFirstConnect = true
        Log.i(TAG, "Stopping scan")
        bluetoothLeScanner?.stopScan(leScanCallback())
        CoroutineScope(Dispatchers.IO).launch {
            bleControlManagers.values.forEach { it.disconnect().enqueue() }
            foundDevices.clear()
            deviceQueue.clear()
            deviceQueueProcessed.clear()
            bleControlManagers.clear()
            Log.d(TAG, "Scan stopped and device queue cleared")
        }
    }

    @SuppressLint("MissingPermission")
    private fun leScanCallback() = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            if (stopRequested) return
            val device = result.device
            val deviceName = device.name ?: return
            val requiredSubstring = when (letter) {
                "E" -> "SatelliteVoice"
                "D" -> "SatelliteOnline"
                else -> null
            }
            if (requiredSubstring != null && !deviceName.contains(requiredSubstring)) return

            val startLastFour = startR.toString().takeLast(4)
            val endLastFour = endR.toString().takeLast(4)
            val lastFourDigits = deviceName.takeLast(4).toIntOrNull() ?: return

            if (lastFourDigits in (startLastFour.toInt()..endLastFour.toInt())) {
                if (device.address !in bannedDevices.map { it.address } &&
                    device.address !in deviceQueue.map { it.address } &&
                    device.address !in foundDevices.map { it.address } &&
                    device.address !in checkedDevices.map { it.address }) {
                    deviceQueue.add(device)
                    foundDevices.add(device)
                    if (bleControlManagers.size < MAX_CONNECTIONS) {
                        connectionToAnotherDevice()
                    }
                }
            }
        }

    }

    fun updateReportViewModel(command: String) {
        deviceProcessor.updateReportViewModel(command, unCheckedDevices, checkedDevices, scanning.value)
    }

    @SuppressLint("MissingPermission")
    fun connectionToAnotherDevice() {
        Log.d(TAG, "Attempting to connect to another device. Counter: $counter")
        when {
            deviceQueue.isNotEmpty() || bleControlManagers.isNotEmpty() || counter != 0 -> {
                if (deviceQueue.isNotEmpty() && bleControlManagers.size < MAX_CONNECTIONS) {
                    val currentDevice = deviceQueue.remove()
                    if (currentDevice.address !in bannedDevices.map { it.address }) {
                        val bleControlManager = BleControlManager(context)
                        bleControlManagers[currentDevice.address] = bleControlManager
                        Logger.d(
                            TAG,
                            "Attempting to connect to device: ${currentDevice.name} (${currentDevice.address})"
                        )
                        setupBleControlManager(bleControlManager)

                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                bleControlManager.connect(currentDevice)
                                    .done { device ->
                                        Log.d(TAG, "Connected to device ${device.name}")
                                        bleControlManager.sendPinCommand("master", EntireCheck.PIN_C0DE)
                                        foundDevices.remove(device)
                                        unCheckedDevices.remove(device)
                                        deviceQueueProcessed.add(device)
                                    }
                                    .fail { device, status ->
                                        Log.e(TAG, "Failed to connect to device ${device.name}: $status")
                                        errorMessage = "Failed to connect: $status"
                                        foundDevices.remove(device)
                                        if (!stopRequested && device.address !in checkedDevices.map { it.address }) {
                                            if (!unCheckedDevices.contains(device))
                                                unCheckedDevices.add(device)
                                            deviceQueue.add(device)
                                        }
                                        bleControlManagers.remove(device.address)
                                        bleControlManager.close()
                                        connectionToAnotherDevice()
                                    }
                                    .enqueue()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error during connection attempt: ${e.message}")
                            }
                        }
                    } else {
                        connectionToAnotherDevice()
                    }
                } else if (bleControlManagers.size >= MAX_CONNECTIONS) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        connectionToAnotherDevice()
                    }, 500)
                }
            }
            else -> {
                if (foundDevices.isEmpty() && counter == 0) {
                    Log.i(TAG, "All devices processed")
                    Logger.i(TAG, "All devices processed. Stopping scan and updating report.")
                    stopScanning()
                    updateReportViewModel("Auto")
                } else {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (scanning.value) {
                            connectionToAnotherDevice()
                        }
                    }, 500)
                }
            }
        }
    }

    private fun setupBleControlManager(bleControlManager: BleControlManager) {
        bleControlManager.setBleCallbackEvent(object : BleCallbackEvent {
            override fun onPinCheck(pin:String) {
                if(pin.contains("pin.ok")){
                    Log.i(TAG, "Pin code is correct")
                    bleControlManager.sendCommand("serial", EntireCheck.HW_VER)
                }else if (pin.contains("pin.error")){
                    Log.e(TAG,"Pin is error")
                    val device = bleControlManager.getConnectedDevice()
                    if (device != null) {
                        bannedDevices.add(device)
                    }
                    disconnectAndCleanup(bleControlManager, device)
                }

            }
            override fun onVersionCheck(version: String) {

                Log.e(TAG,"Version :$version")
                val versionPrefix = version.firstOrNull()
                val versionNumber = version.substring(1).toLongOrNull()
                val device = bleControlManager.getConnectedDevice()

                if (versionPrefix == null || versionNumber == null || device == null) {
                    Log.e(TAG, "Invalid version format or no connected device")
                    disconnectAndCleanup(bleControlManager, device)
                    return
                }
                Logger.i(TAG, "Device version check: $version for device ${device.address}")

                Log.d(TAG, "Version received: $version")

                if (versionNumber in startR..endR && versionPrefix.toString().contains(letter)) {
                    Log.d(TAG, "Device serial number in range!")
                    checkedDevices.add(device)
                    unCheckedDevices.remove(device)
                    bleControlManager.sendCommand("ble.off", EntireCheck.default_command)
                    counter--
                    disconnectAndCleanup(bleControlManager, device)
                } else {
                    Log.d(TAG, "Device serial number out of range. Added to banned list.")
                    Log.e(TAG,"Device version!: $version")
                    Log.e(TAG,"range: $startR and $endR")
                    Log.e(TAG,"letter for prefix: $letter")
                    Log.e(TAG,"Device version prefix: $versionPrefix")
                    bannedDevices.add(device)
                    disconnectAndCleanup(bleControlManager, device)
                }
            }
        })
    }

    private fun disconnectAndCleanup(bleControlManager: BleControlManager, device: BluetoothDevice?) {
        CoroutineScope(Dispatchers.IO).launch {
            bleControlManager.disconnect().done {
                bleControlManager.close()
                device?.let { bleControlManagers.remove(it.address) }
                deviceProcessor.setErrorMessage(null)
                connectionToAnotherDevice()
            }.enqueue()
        }
    }

    override fun onEvent(event: String) {
        when {
            event.contains("Modify") -> {
                if (scanning.value){
                    stopScanning()
                    updateReportViewModel("Auto")
                }
            }
            event.contains("Deleted") -> {
                if (scanning.value) {
                    stopScanning()
                    updateReportViewModel("Auto")
                }
            }
            event.contains("Auto") -> {
                letter = deviceTypeToLetter[sharedData.typeOfDevice.value.toString()].toString()
                deviceTypeLetter.value = letter
                Log.e("ScanViewModelEvent","letter is: $letter")
                sharedData.addressRange.value?.let { range ->
                    val start = range.first.toLongOrNull()
                    val end = range.second.toLongOrNull()
                    if (start != null && end != null) {
                        scanLeDevice(letter, start, end)
                    } else {
                        Log.e(TAG, "Invalid address range")
                    }
                } ?: Log.e(TAG, "Address range is null")
            }
        }
    }
}