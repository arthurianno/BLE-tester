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
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject

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
    private val maxConnections = 7

    init {
        reportViewModel.registerCallback(this)
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
    fun scanLeDevice(letter: String, start: Long, end: Long) {
        val typeOfLetterForReport = deviceTypeToLetterToReport[letter]
        reportViewModel._addressRange.value = Pair(start.toString(), end.toString())
        Log.e("DevicesListScreen", "this is range ${Pair(start, end)}")
        reportViewModel.typeOfDevice.value = typeOfLetterForReport
        Log.e("DevicesListScreen", "this is letter to report $typeOfLetterForReport")
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
            bluetoothLeScanner?.startScan(leScanCallback())
            Log.i("SCAN", "SCANNING")
            Logger.i("ScanViewModel", "SCANNING")
        } else {
            Log.e("ScanCheckLog", "VALUE OF SCAN ${scanning.value}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        bleControlManagers.values.forEach { it.disconnect().enqueue() }
        toastMessage.value = "Остановка сканирования!"
        isScanning = false
        stopRequested = true
        scanning.value = false
        isFirstConnect = true
        Log.i("SCAN", " STOP SCANNING")
        foundDevices.clear()
        deviceQueue.clear()
        deviceQueueProcessed.clear()
        bleControlManagers.clear()
        Log.e("ScanViewModel", "Сканирование остановлено и очередь устройств очищена")
    }

    @SuppressLint("MissingPermission")
    fun createReportItems(deviceList: List<BluetoothDevice>, status: String): List<ReportItem> {
        return deviceList.map { device ->
            ReportItem(
                device = device.name ?: "Unknown Device",
                deviceAddress = device.address,
                status = status,
                interpretation = errorMessage?.let { errorMessage } ?: "Устройство не в эфире!"
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
            Log.i("ScanViewModel", "${unCheckedDevices.toList()}")
        } else {
            Log.i("ScanViewModel", "Сканирование все еще идет, отчет не обновляется")
        }
    }

    @SuppressLint("MissingPermission")
    private fun leScanCallback() = object : ScanCallback() {
        @SuppressLint("MissingPermission", "SuspiciousIndentation")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            if (stopRequested) return
            val device = result.device
            val deviceName = device.name ?: return
            val startLastFour = startR.toString().takeLast(4)
            val endLastFour = endR.toString().takeLast(4)
            if (deviceName.contains("Satellite")) {
                val lastFourDigits = deviceName.takeLast(4).toInt()
                if (lastFourDigits in (startLastFour.toInt()..endLastFour.toInt())) {
                    if (device.address !in bannedDevices.map { it.address }) {
                        if (device.address !in deviceQueue.map { it.address } &&
                            device.address !in foundDevices.map { it.address } &&
                            device.address !in checkedDevices.map { it.address }) {
                            deviceQueue.add(device)
                            foundDevices.add(device)
                            Log.e("ScanViewModel", "device $deviceName")
                            if (bleControlManagers.size < maxConnections) {
                                connectionToAnotherDevice()
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission", "SuspiciousIndentation")
    fun connectionToAnotherDevice() {
        Log.i("ScanCounter","Counter: $counter")
        if (deviceQueue.isNotEmpty() || bleControlManagers.isNotEmpty() || counter != 0) {
            if (deviceQueue.isNotEmpty() && bleControlManagers.size < maxConnections) {
                val currentDevice = deviceQueue.remove()
                if (currentDevice.address !in bannedDevices.map { it.address }) {
                    val bleControlManager = BleControlManager(context)
                    bleControlManagers[currentDevice.address] = bleControlManager

                    setupBleControlManager(bleControlManager)

                    bleControlManager.connect(currentDevice)
                        .done { device ->
                            Log.d("BleControlManager", "Connected to device ${device.name}")
                            bleControlManager.sendPinCommand("master", EntireCheck.PIN_C0DE)
                            foundDevices.remove(device)
                            unCheckedDevices.remove(device)
                            deviceQueueProcessed.add(device)
                        }
                        .fail { device, status ->
                            Log.e("BleControlManager", "Failed to connect to device ${device.name}: $status")
                            errorMessage = "Failed to connect: $status"
                            foundDevices.remove(device)
                            if (!stopRequested && device.address !in checkedDevices.map { it.address }) {
                                if(!unCheckedDevices.contains(device))
                                    unCheckedDevices.add(device)
                                deviceQueue.add(device)
                            }
                            bleControlManagers.remove(device.address)
                            bleControlManager.close()
                            connectionToAnotherDevice()
                        }
                        .enqueue()
                } else {
                    connectionToAnotherDevice()  // Пропускаем забаненное устройство
                }
            } else if (bleControlManagers.size >= maxConnections) {
                // Ждем освобождения соединений
                Handler(Looper.getMainLooper()).postDelayed({
                    connectionToAnotherDevice()
                }, 500)
            }
        } else {
            // Проверяем, все ли устройства обработаны
            if (foundDevices.isEmpty() && counter == 0) {
                Log.e("ScanViewModel", "Все устройства обработаны")
                stopScanning()
                updateReportViewModel("")
            } else {
                // Если еще остались необработанные устройства, продолжаем сканирование
                Handler(Looper.getMainLooper()).postDelayed({
                    if(scanning.value){
                        connectionToAnotherDevice()
                    }
                }, 500)
            }
        }
    }

    private fun setupBleControlManager(bleControlManager: BleControlManager) {
        bleControlManager.setBleCallbackEvent(object : BleCallbackEvent {
            override fun onHandleCheck() {
                Log.e("onHandleCheck", "connection To Another Dev after Callback")

            }
            override fun onVersionCheck(version: String) {
                val versionPrefix = version.firstOrNull()
                val versionNumber = version.substring(1).toLongOrNull()
                val device = bleControlManager.getConnectedDevice()

                if (versionPrefix == null || versionNumber == null || device == null) {
                    Log.e("ScanViewModel", "Invalid version format or no connected device")
                    bleControlManager.disconnect().done {
                        bleControlManager.close()
                        bleControlManagers.remove(device?.address)
                        connectionToAnotherDevice()
                    }.enqueue()
                    return
                }

                Log.e("ScanViewModel", "Version: $version")

                if (versionNumber in startR..endR && versionPrefix.toString().contains(letter)) {
                    Log.e("ScanViewModel", "Device serial number in range!")
                    Log.e("ScanViewModel", "Device added to checkedDevices: $device")
                    checkedDevices.add(device)
                    unCheckedDevices.remove(device)  // Добавьте эту строку здесь
                    bleControlManager.sendCommand("ble.off", EntireCheck.default_command)
                    counter--
                    bleControlManager.disconnect().done {
                        bleControlManager.close()
                        bleControlManagers.remove(device.address)
                        connectionToAnotherDevice()
                    }.enqueue()
                } else {
                    Log.e("ScanViewModel", "Device serial number out of range!")
                    Log.e("ScanViewModel", "Device added to BANNED LIST!")
                    bannedDevices.add(device)
                    bleControlManager.disconnect().done {
                        bleControlManager.close()
                        bleControlManagers.remove(device.address)
                        connectionToAnotherDevice()
                    }.enqueue()
                }
            }
        })
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
                reportViewModel._addressRange.value?.first?.toLong()?.let {
                    reportViewModel._addressRange.value?.second?.toLong()?.let { it1 ->
                        scanLeDevice(
                            letter,
                            it,
                            it1
                        )
                    }
                }
            }
        }
    }

    companion object {
        val entireCheckQueue: Queue<EntireCheck> = LinkedList()
    }
}