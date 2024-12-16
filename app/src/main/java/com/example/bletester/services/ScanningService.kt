package com.example.bletester.services

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.example.bletester.ble.BleCallbackEvent
import com.example.bletester.ble.BleControlManager
import com.example.bletester.core.DeviceProcessor
import com.example.bletester.core.EntireCheck
import com.example.bletester.ui.theme.log.Logger
import com.example.bletester.ui.theme.report.ReportViewModel
import com.example.bletester.utils.FileModifyEvent
import com.example.bletester.utils.IniUtil
import com.example.bletester.utils.SharedData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject

@Suppress("DEPRECATION")
class ScanningService @Inject constructor(
    reportViewModel: ReportViewModel,
    private val deviceProcessor: DeviceProcessor,
    private val sharedData: SharedData,
    private val iniUtil: IniUtil,
    private val sharedPreferences: SharedPreferences,
    private val bleControlManager: BleControlManager
) : FileModifyEvent {

    companion object {
        private val TAG = this::class.java.simpleName
    }

    val toastMessage = MutableStateFlow<String?>(null)
    private val deviceQueue = ConcurrentLinkedQueue<BluetoothDevice>()
    var foundDevices = mutableSetOf<BluetoothDevice>()
    private val allDevices = mutableListOf<BluetoothDevice>()
    val unCheckedDevices = mutableStateListOf<BluetoothDevice>()
    val checkedDevices = mutableListOf<BluetoothDevice>()
    val checkedDevicesUi = mutableStateListOf<String>()
    var bannedDevices = mutableStateListOf<BluetoothDevice>()
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val bluetoothLeScanner = adapter?.bluetoothLeScanner
    private val settings: ScanSettings
    private val filters: List<ScanFilter>
    var scanning = MutableStateFlow(false)
    private var stopRequested = false
    private var startR: Long = 0L
    private var endR: Long = 0L
    private var counter = 0
    private var letter = ""
    val deviceTypeLetter = MutableStateFlow("")
    private var currentCount: Int = 0
    private var currentDevice: BluetoothDevice? = null
    private var isFirstConnect = true

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

    private var connectionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        reportViewModel.registerCallback(this)
        settings = buildSettings()
        filters = buildFilter()
        currentCount = sharedPreferences.getInt("count_current", 0)
        setupBleControlManager(bleControlManager)
    }

    private fun buildSettings() = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
        .build()

    private fun buildFilter() = listOf(
        ScanFilter.Builder().build()
    )

    @SuppressLint("MissingPermission")
    fun scanLeDevice(letter: String, start: Long, end: Long) {
        sharedPreferences.edit().clear().apply()
        Logger.i(TAG, "Scanning started for device type: $letter, range: $start - $end")
        val typeOfLetterForReport = deviceTypeToLetterToReport[letter]
        sharedData.addressRange.value = Pair(start.toString(), end.toString())
        Log.d(TAG, "Address range: ${Pair(start, end)}")
        sharedData.typeOfDevice.value = typeOfLetterForReport
        Log.d(TAG, "Device type for report: $typeOfLetterForReport")

        val (approvedMacs, _, savedRange) = iniUtil.loadApprovedDevicesFromCurrentReport()
        if (savedRange != null && savedRange.first.toLong() == start && savedRange.second.toLong() == end) {
            Log.i(TAG, "Диапазон совпадает с сохраненным. Загружаем одобренные устройства.")
            checkedDevices.clear()
            checkedDevicesUi.clear()
            approvedMacs.forEach { mac ->
                adapter?.getRemoteDevice(mac)?.let { device ->
                    if (checkedDevices.none { it.address == device.address }) {
                        Log.i(TAG, "Device is ${device.name} and ${device.address}")
                        checkedDevices.add(device)
                    }
                }
            }
            Log.i(TAG, "Загружено ${approvedMacs.size} одобренных устройств.")
            counter = ((end - start) + 1L).toInt() - approvedMacs.size
        } else {
            Log.i(TAG, "Диапазон не совпадает с сохраненным. Начинаем новое сканирование.")
            counter = ((end - start) + 1L).toInt()
            checkedDevices.clear()
            checkedDevicesUi.clear()
        }

        toastMessage.value = "Сканирование!"
        iniUtil.isFirstUpdate = true
        startR = start
        endR = end
        stopRequested = false
        scanning.value = false
        deviceQueue.clear()
        foundDevices.clear()
        isFirstConnect = true
        unCheckedDevices.clear()
        bannedDevices.clear()

        if (!scanning.value) {
            scanning.value = true
            if (!connectionScope.isActive) {
                connectionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            }
            bluetoothLeScanner?.startScan(filters, settings, leScanCallback()) ?: Log.e(
                TAG,
                "BluetoothLeScanner равен null"
            )
            Log.i(TAG, "Сканирование начато")
            Log.i(TAG, " first connect to: $isFirstConnect")
            Logger.i(TAG, "Сканирование начато")
        } else {
            Log.w(TAG, "Сканирование уже выполняется")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        Logger.i(TAG, "Остановка сканирования и отключение всех устройств")
        toastMessage.value = "Стоп"
        stopRequested = true
        counter = 0
        currentCount = 0
        scanning.value = false
        letter = ""
        Log.i(TAG, "Остановка сканирования")
        bluetoothLeScanner?.stopScan(leScanCallback())
        connectionScope.cancel()
        CoroutineScope(Dispatchers.IO).launch {
            if (bleControlManager.isConnected) {
                bleControlManager.disconnect().await()
            }
            bleControlManager.close()
            foundDevices.clear()
            deviceQueue.clear()
            currentDevice = null
            Log.d(TAG, "Сканирование остановлено и очередь устройств очищена")
        }
    }

    @SuppressLint("MissingPermission")
    private fun leScanCallback() = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            if (stopRequested) return
            val device = result.device ?: return
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
                    if (isFirstConnect) {
                        isFirstConnect = false
                        startConnectionProcess()
                    }
                }
            }
        }
    }

    fun updateReportViewModel(command: String) {
        deviceProcessor.updateReportViewModel(
            command,
            unCheckedDevices.toList(),
            checkedDevices.toList(),
            bannedDevices.toList(),
            scanning.value
        )
    }

    private fun startConnectionProcess() {
        Log.e(TAG, "counter : $counter, value scanning: ${scanning.value}, ")
        if (scanning.value && (deviceQueue.isNotEmpty() || counter > 0)) {
            processNextDevice()
        }
        if (foundDevices.isEmpty() && counter == 0) {
            stopScanning()
            updateReportViewModel("Auto")
        }
    }

    @SuppressLint("MissingPermission")
    private fun processNextDevice() {
        while (deviceQueue.isNotEmpty() && scanning.value && counter > 0) {
            val currentDevice = deviceQueue.poll() ?: continue


            Log.d(TAG, "Processing device: ${currentDevice.address}, currentCount: $currentCount")


            if (currentDevice.name == null) {
                Log.w(TAG, "Skipping device with null name: ${currentDevice.address}")
                continue
            }

            if (currentDevice.address !in bannedDevices.map { it.address }) {
                currentCount++
                connectionScope.launch {
                    connectToDevice(currentDevice)
                }
                break
            }
        }


        if (deviceQueue.isEmpty() && counter == 0) {
            stopScanning()
            updateReportViewModel("Auto")
        }
    }

    @SuppressLint("MissingPermission")
    private fun getDeviceName(device: BluetoothDevice): String {
        device.name?.let { return it }

        allDevices.find { it.address == device.address }?.name?.let { return it }

        return device.address
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        if (allDevices.none { it.address == device.address }) {
            allDevices.add(device)
        }

        try {
            bleControlManager.connect(device).retry(2, 200).await()
            Log.d(TAG, "Connected to device ${device.name ?: "Unknown"}")
            bleControlManager.sendPinCommand(device, "master", EntireCheck.PIN_C0DE)
            foundDevices.remove(device)
            unCheckedDevices.remove(device)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to device ${device.name}: ${e.message}")
            foundDevices.remove(device)
            if (!stopRequested && device.address !in checkedDevices.map { it.address }) {
                if (!unCheckedDevices.contains(device))
                    unCheckedDevices.add(device)
                deviceQueue.add(device)
            }

            deviceProcessor.setErrorMessage(null)
            startConnectionProcess()
        }
    }

    private fun setupBleControlManager(bleControlManager: BleControlManager) {
        bleControlManager.setBleCallbackEvent(object : BleCallbackEvent {
            @SuppressLint("MissingPermission")
            override fun onPinCheck(device: BluetoothDevice, pin: String) {
                when {
                    pin.contains("pin.ok") -> {
                        Log.i(TAG, "Pin code is correct for device ${device.name}")
                        bleControlManager.sendCommand(device, "serial", EntireCheck.HW_VER)
                        Log.d(TAG, "sendCommand SERIAL to device ${device.name ?: "Unknown"}")
                    }
                    pin.contains("pin.error") -> {
                        Log.e(TAG, "Pin is error for device ${device.address}")
                        bannedDevices.add(device)
                        disconnectAndCleanup()
                    }
                    pin.contains("GATT PIN ATTR ERROR") -> {
                        deviceQueue.add(device)
                        disconnectAndCleanup()
                    }
                }
            }

            @SuppressLint("MissingPermission")
            override fun onVersionCheck(device: BluetoothDevice, version: String) {
                Log.e(TAG, "Version: $version for device ${device.name}")
                val versionPrefix = version.firstOrNull()
                val versionNumber = version.substring(1).toLongOrNull()

                if (versionPrefix == null || versionNumber == null) {
                    Log.e(TAG, "Invalid version format or no connected device")
                    disconnectAndCleanup()
                    return
                }

                if (versionNumber in startR..endR && versionPrefix.toString().contains(letter)) {
                    if (checkedDevices.none { it.address == device.address }) {
                        checkedDevices.add(device)
                        val safeName = getDeviceName(device)
                        checkedDevicesUi.add(safeName)
                    } else {
                        Log.e(TAG, "Device with the same address already exists: ${device.address}")
                    }
                    unCheckedDevices.remove(device)

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            iniUtil.updateSummaryFileDynamically(checkedDevices.size)
                            iniUtil.updateCurrentFileDynamically(device)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating summary file: ${e.message}")
                        }
                    }
                    bleControlManager.sendCommand(device, "ble.off", EntireCheck.default_command)
                    disconnectAndCleanup()
                } else {
                        Log.d(TAG, "Device serial number out of range. Added to banned list.")
                    bannedDevices.add(device)
                    disconnectAndCleanup()
                }
            }
        })
    }

    private fun disconnectAndCleanup() {
        if (bleControlManager.isConnected) {
            bleControlManager.disconnect().done {
                Log.i(TAG, "Disconnect done!")
                startConnectionProcess()
            }.fail { device, status ->
                Log.e(TAG, "Disconnect fail! $device and $status")
            }.enqueue()
        }
    }

    override fun onEvent(event: String) {
        when {
            event.contains("Modify") -> {
                if (scanning.value) {
                    stopScanning()
                    Log.e(TAG, "Scanning stop from onEvent Modify")
                    updateReportViewModel("Auto")
                }
            }
            event.contains("Deleted") -> {
                if (scanning.value) {
                    stopScanning()
                    Log.e(TAG, "Scanning stop from onEvent Deleted")
                    updateReportViewModel("Auto")
                }
            }
            event.contains("Auto") -> {
                letter = deviceTypeToLetter[sharedData.typeOfDevice.value.toString()].toString()
                deviceTypeLetter.value = letter
                Log.e("ScanViewModelEvent", "letter is: $letter")
                sharedData.addressRange.value?.let { range ->
                    val start = range.first.toLongOrNull()
                    val end = range.second.toLongOrNull()
                    if (start != null && end != null) {
                        scanLeDevice(letter, start, end)
                    } else {
                        Log.e(TAG, "Invalid address range, retrying in 1 second")
                        connectionScope.launch {
                            delay(1000)
                            onEvent("Auto")
                        }
                    }
                } ?: run {
                    Log.e(TAG, "Address range is null, retrying in 1 second")
                    connectionScope.launch {
                        delay(1000)
                        onEvent("Auto")
                    }
                }
            }
        }
    }
}