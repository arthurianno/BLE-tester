package com.example.bletester.services

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.example.bletester.ble.BleCallbackEvent
import com.example.bletester.ble.BleControlManager
import com.example.bletester.core.DeviceProcessor
import com.example.bletester.core.EntireCheck
import com.example.bletester.ui.theme.log.Logger
import com.example.bletester.ui.theme.report.ReportViewModel
import com.example.bletester.utils.FileModifyEvent
import com.example.bletester.utils.IniUtil
import com.example.bletester.utils.SharedData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject

private const val TAG = "ScanningService"
private const val MAX_CONNECTIONS = 3

@Suppress("DEPRECATION")
class ScanningService @Inject constructor(
    private val context: Context,
    reportViewModel: ReportViewModel,
    private val deviceProcessor: DeviceProcessor,
    private val sharedData: SharedData,
    private val iniUtil: IniUtil
) : FileModifyEvent {

    val toastMessage = MutableStateFlow<String?>(null)
    private val deviceQueue = ConcurrentLinkedQueue<BluetoothDevice>()
    var foundDevices = mutableSetOf<BluetoothDevice>()
    val unCheckedDevices = mutableStateListOf<BluetoothDevice>()
    val checkedDevices = mutableStateListOf<BluetoothDevice>()
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val bluetoothLeScanner = adapter?.bluetoothLeScanner
    private val settings: ScanSettings
    private val filters: List<ScanFilter>
    var scanning = MutableStateFlow(false)
    private var stopRequested = false
    private var startR: Long = 0L
    private var endR: Long = 0L
    private var counter = 0
    var bannedDevices = mutableStateListOf<BluetoothDevice>()
    private var letter = ""
    private var isFirstConnect = true
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
    private var connectionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        reportViewModel.registerCallback(this)
        settings = buildSettings()
        filters = buildFilter()
    }

    private fun buildSettings() =
        ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
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

        // Загрузка одобренных устройств из current.ini
        val (approvedDevices, savedRange) = iniUtil.loadApprovedDevicesFromCurrentReport()

        if (savedRange != null && savedRange.first.toLong() == start && savedRange.second.toLong() == end) {
            Log.i(TAG, "Диапазон совпадает с сохраненным. Загружаем одобренные устройства.")
            approvedDevices.forEach { address ->
                val device = adapter?.getRemoteDevice(address)
                if (device != null && !checkedDevices.any { it.address == device.address }) {
                    checkedDevices.add(device)
                }
            }
            Log.i(TAG, "Загружено ${approvedDevices.size} одобренных устройств.")
            counter = ((end - start) + 1L).toInt() - approvedDevices.size
        } else {
            Log.i(TAG, "Диапазон не совпадает с сохраненным. Начинаем новое сканирование.")
            counter = ((end - start) + 1L).toInt()
        }

        toastMessage.value = "Сканирование!"
        iniUtil.isFirstUpdate = true
        startR = start
        endR = end
        stopRequested = false
        scanning.value = false
        deviceQueue.clear()
        foundDevices.clear()
        unCheckedDevices.clear()
        bannedDevices.clear()
        connectionScope.cancel()
        connectionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        if (!scanning.value) {
            scanning.value = true
            bluetoothLeScanner?.startScan(filters, settings, leScanCallback())
                ?: Log.e(TAG, "BluetoothLeScanner равен null")
            Log.i(TAG, "Сканирование начато")
            Logger.i(TAG, "Сканирование начато")
            //generateFakeDevices(250)
            startConnectionProcess()
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
        scanning.value = false
        letter = ""
        isFirstConnect = true
        Log.i(TAG, "Остановка сканирования")
        bluetoothLeScanner?.stopScan(leScanCallback())
        connectionScope.cancel()
        CoroutineScope(Dispatchers.Default).launch {
            bleControlManagers.values.forEach { it.disconnect().enqueue() }
            foundDevices.clear()
            deviceQueue.clear()
            checkedDevices.clear()
            bleControlManagers.clear()
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

                }
            }
        }
    }

    fun updateReportViewModel(command: String) {
        deviceProcessor.updateReportViewModel(command, unCheckedDevices, checkedDevices,bannedDevices, scanning.value)
    }

    private fun startConnectionProcess() {
        connectionScope.launch {
            Log.e(TAG,"counter : $counter, active: $isActive, value scanning: ${scanning.value}, ")
            while (isActive && scanning.value && (deviceQueue.isNotEmpty() || bleControlManagers.isNotEmpty() || counter > 0)) {
                while (deviceQueue.isNotEmpty() && bleControlManagers.size < MAX_CONNECTIONS) {
                    processNextDevice()
                }
                delay(100) // Короткая задержка для предотвращения тесного цикла
            }
            if (foundDevices.isEmpty() && counter == 0) {
                stopScanning()
                updateReportViewModel("Auto")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun processNextDevice() {
        if (deviceQueue.isNotEmpty() && bleControlManagers.size < MAX_CONNECTIONS) {
            val currentDevice = deviceQueue.poll() ?: return
            if(currentDevice.name == null){
                Log.w(TAG, "Skipping device with null name: ${currentDevice.address}")
                return
            }
            if (currentDevice.address !in bannedDevices.map { it.address }) {
                val bleControlManager = BleControlManager(context)
                bleControlManagers[currentDevice.address] = bleControlManager
                setupBleControlManager(bleControlManager)
                connectionScope.launch {
                    connectToDevice(currentDevice, bleControlManager)
                }
            }
        }else{
            Log.e(TAG,"Error  Queue is empty: ${deviceQueue.isEmpty()} or ${bleControlManagers.size} < $MAX_CONNECTIONS")
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectToDevice(device: BluetoothDevice, bleControlManager: BleControlManager) {
        try {
            withTimeout(10000) {
                bleControlManager.connect(device).retry(2, 50)
                    .await()
                Log.d(TAG, "Connected to device ${device.name ?: "Unknown"}")
                bleControlManager.sendPinCommand(device,"master", EntireCheck.PIN_C0DE)
                foundDevices.remove(device)
                unCheckedDevices.remove(device)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to device ${device.name}: ${e.message}")
            foundDevices.remove(device)
            if (!stopRequested && device.address !in checkedDevices.map { it.address }) {
                if (!unCheckedDevices.contains(device))
                    unCheckedDevices.add(device)
                deviceQueue.add(device)
            }
            bleControlManagers.remove(device.address)
            bleControlManager.close()
        }
    }

    private fun setupBleControlManager(bleControlManager: BleControlManager) {
        bleControlManager.setBleCallbackEvent(object : BleCallbackEvent {
            override fun onPinCheck(pin: String) {
                connectionScope.launch {
                    if (pin.contains("pin.ok")) {
                        Log.i(TAG, "Pin code is correct")
                        val device = bleControlManager.getConnectedDevice()
                        bleControlManager.sendCommand(device!!,"serial", EntireCheck.HW_VER)
                    } else if (pin.contains("pin.error")) {
                        Log.e(TAG, "Pin is error")
                        val device = bleControlManager.getConnectedDevice()
                        if (device != null) {
                            bannedDevices.add(device)
                        }
                        disconnectAndCleanup(bleControlManager, device)
                    }else if(pin.contains("GATT PIN ATTR ERROR")){
                        val device = bleControlManager.getConnectedDevice()
                        if(device != null){
                            deviceQueue.add(device)
                        }
                        disconnectAndCleanup(bleControlManager,device)
                    }
                }
            }

            override fun onVersionCheck(version: String) {
                Log.e(TAG, "Version :$version")
                connectionScope.launch {
                    val versionPrefix = version.firstOrNull()
                    val versionNumber = version.substring(1).toLongOrNull()
                    val device = bleControlManager.getConnectedDevice()

                    if (versionPrefix == null || versionNumber == null || device == null) {
                        Log.e(TAG, "Invalid version format or no connected device")
                        disconnectAndCleanup(bleControlManager, device)
                        return@launch
                    }
                    Logger.i(TAG, "Device version check: $version for device ${device.address}")

                    Log.d(TAG, "Version received: $version")

                    if (versionNumber in startR..endR && versionPrefix.toString()
                            .contains(letter)
                    ) {
                        Log.d(TAG, "Device serial number in range!")

                        if (checkedDevices.none { it.address == device.address }) {
                            checkedDevices.add(device)
                            Log.i("Устройство добавлено", device.address)
                        } else {
                            Log.e("Устройство с таким адресом уже существует", device.address)
                        }
                        unCheckedDevices.remove(device)
                        launch(Dispatchers.IO) {
                            try {
                                iniUtil.updateSummaryFileDynamically(checkedDevices.size)
                                iniUtil.updateCurrentFileDynamically(device)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error updating summary file: ${e.message}")
                            }
                        }
                        bleControlManager.sendCommand(
                            device,
                            "ble.off",
                            EntireCheck.default_command
                        )
                        counter--
                        Log.d(TAG, "counter is :$counter")
                        disconnectAndCleanup(bleControlManager, device)
                    } else {
                        Log.d(TAG, "Device serial number out of range. Added to banned list.")
                        Log.e(TAG, "Device version!: $version")
                        Log.e(TAG, "range: $startR and $endR")
                        Log.e(TAG, "letter for prefix: $letter")
                        Log.e(TAG, "Device version prefix: $versionPrefix")
                        bannedDevices.add(device)
                        disconnectAndCleanup(bleControlManager, device)
                    }
                }
            }
        })
    }

    private fun disconnectAndCleanup(bleControlManager: BleControlManager, device: BluetoothDevice?) {
        connectionScope.launch {
            bleControlManager.disconnect().await()
            bleControlManager.cleanup()
            bleControlManager.close()
            device?.let { bleControlManagers.remove(it.address) }
            deviceProcessor.setErrorMessage(null)
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
                            onEvent("Auto") // Повторяем попытку через секунду
                        }
                    }
                } ?: run {
                    Log.e(TAG, "Address range is null, retrying in 1 second")
                    connectionScope.launch {
                        delay(1000)
                        onEvent("Auto") // Повторяем попытку через секунду
                    }
                }
            }
        }
    }
}

