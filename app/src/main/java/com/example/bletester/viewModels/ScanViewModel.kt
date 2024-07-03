package com.example.bletester.viewModels

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
import androidx.lifecycle.ViewModel
import com.example.bletester.EntireCheck
import com.example.bletester.Logger
import com.example.bletester.ReportItem
import com.example.bletester.ble.BleCallbackEvent
import com.example.bletester.ble.BleControlManager
import com.example.bletester.ble.FileModifyEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject

private const val TAG = "ScanViewModel"
private const val MAX_CONNECTIONS = 5

@Suppress("DEPRECATION")
@HiltViewModel
@SuppressLint("StaticFieldLeak")
class ScanViewModel @Inject constructor(
    private val context: Context,
    private val reportViewModel: ReportViewModel
) : ViewModel(), FileModifyEvent {
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
    val progress = MutableStateFlow(0f)
    private var letter = ""
    private var isFirstConnect = true
    private var isScanning: Boolean = false
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
        reportViewModel._addressRange.value = Pair(start.toString(), end.toString())
        Log.d(TAG, "Address range: ${Pair(start, end)}")
        reportViewModel.typeOfDevice.value = typeOfLetterForReport
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
        toastMessage.value = "Остановка сканирования!"
        isScanning = false
        stopRequested = true
        scanning.value = false
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
    fun createReportItems(deviceList: List<BluetoothDevice>, status: String): List<ReportItem> {
        return deviceList.map { device ->
            ReportItem(
                device = device.name ?: "Unknown Device",
                deviceAddress = device.address,
                status = status,
                interpretation = errorMessage ?: "Устройство не в эфире!"
            )
        }
    }

    fun updateReportViewModel(command: String) {
        if (!scanning.value) {
            val uncheckedReportItems = createReportItems(unCheckedDevices.distinct(), "Unchecked")
            val approvedReportItems = createReportItems(checkedDevices.distinct(), "Checked")
            if (command.contains("Manual")) {
                reportViewModel.updateReportItemsManual(uncheckedReportItems, approvedReportItems)
            } else {
                reportViewModel.updateReportItems(uncheckedReportItems, approvedReportItems)
            }
            Log.d(TAG, "Report updated with ${unCheckedDevices.size} unchecked and ${checkedDevices.size} checked devices")
        } else {
            Log.w(TAG, "Scanning is still in progress, report not updated")
        }
    }

    @SuppressLint("MissingPermission")
    private fun leScanCallback() = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            if (stopRequested) return
            val device = result.device
            val deviceName = device.name ?: return
            val startLastFour = startR.toString().takeLast(4)
            val endLastFour = endR.toString().takeLast(4)

            when {
                !deviceName.contains("Satellite") -> return
                else -> {
                    val lastFourDigits = deviceName.takeLast(4).toIntOrNull() ?: return
                    if (lastFourDigits in (startLastFour.toInt()..endLastFour.toInt())) {
                        if (device.address !in bannedDevices.map { it.address } &&
                            device.address !in deviceQueue.map { it.address } &&
                            device.address !in foundDevices.map { it.address } &&
                            device.address !in checkedDevices.map { it.address }) {
                            Logger.d(TAG, "Device added to queue: $deviceName (${device.address})")
                            deviceQueue.add(device)
                            foundDevices.add(device)
                            Log.d(TAG, "Device added to queue: $deviceName")
                            if (bleControlManagers.size < MAX_CONNECTIONS) {
                                connectionToAnotherDevice()
                            }
                        }
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error code: $errorCode")
            stopScanning()
        }
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
                        Logger.d(TAG, "Attempting to connect to device: ${currentDevice.name} (${currentDevice.address})")
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
                    Log.d(TAG, "All devices processed")
                    Logger.i(TAG, "All devices processed. Stopping scan and updating report.")
                    stopScanning()
                    updateReportViewModel("")
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
            override fun onHandleCheck() {
                Log.d(TAG, "Handle check callback received")
            }
            override fun onVersionCheck(version: String) {

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
                connectionToAnotherDevice()
            }.enqueue()
        }
    }

    override fun onEvent(event: String) {
        when {
            event.contains("Modify") -> {
                stopScanning()
                updateReportViewModel("")
            }
            event.contains("Deleted") -> {
                stopScanning()
                updateReportViewModel("")
            }
            event.contains("Auto") -> {
                letter = deviceTypeToLetter[reportViewModel.typeOfDevice.value.toString()].toString()
                reportViewModel._addressRange.value?.let { range ->
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

    companion object {
        val entireCheckQueue: Queue<EntireCheck> = LinkedList()
    }
}